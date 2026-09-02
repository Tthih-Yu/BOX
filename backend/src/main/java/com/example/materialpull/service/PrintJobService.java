package com.example.materialpull.service;

import com.example.materialpull.common.*;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PrintJobService {
    private final PrintJobRepository printJobRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final AuditService auditService;
    private final RealtimePushService pushService;
    private final AppProperties properties;
    private final ExternalHttpClient externalHttpClient;
    private final SystemConfigRepository systemConfigRepository;
    private final DataScopeService dataScopeService;

    // 标签排版参数键：dpi 与物理尺寸(mm)。换打印机/换标签只需在“系统参数”改这三项，无需改代码。
    static final String CFG_DPI = "print.label.dpi";
    static final String CFG_WIDTH_MM = "print.label.width-mm";
    static final String CFG_HEIGHT_MM = "print.label.height-mm";
    // 参考设计画布：80mm×50mm 在 203dpi 下 = 640×400 点。所有坐标都相对该画布，再按实际 dpi/尺寸缩放。
    private static final double REF_W = 640.0;
    private static final double REF_H = 400.0;

    public List<PrintJobEntity> list(String status) {
        PrintJobStatus safeStatus = parseStatus(status);
        if (dataScopeService.isGlobalAdmin()) {
            return safeStatus == null ? printJobRepository.findTop1000ByOrderByCreatedAtDesc()
                    : printJobRepository.findTop1000ByStatusOrderByCreatedAtDesc(safeStatus);
        }
        String factory = dataScopeService.currentFactory();
        List<String> areas = dataScopeService.currentDeliveryAreas();
        return (areas.isEmpty()
                ? printJobRepository.findFactoryWideIncludingTaskFallback(factory, safeStatus, "", PageRequest.of(0, 1000))
                : printJobRepository.findScopedIncludingTaskFallback(factory, areas, safeStatus, "", PageRequest.of(0, 1000))).getContent();
    }

    public Map<String, Object> page(String status, String channel, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 20));
        String safeChannel = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        PrintJobStatus safeStatus = parseStatus(status);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PrintJobEntity> result;
        if (!dataScopeService.isGlobalAdmin()) {
            String factory = dataScopeService.currentFactory();
            List<String> areas = dataScopeService.currentDeliveryAreas();
            result = areas.isEmpty()
                    ? printJobRepository.findFactoryWideIncludingTaskFallback(factory, safeStatus, safeChannel, pageable)
                    : printJobRepository.findScopedIncludingTaskFallback(factory, areas, safeStatus, safeChannel, pageable);
        } else if (safeStatus != null && !safeChannel.isBlank()) {
            result = printJobRepository.findByStatusAndPrintChannelIgnoreCaseOrderByCreatedAtDesc(safeStatus, safeChannel, pageable);
        } else if (safeStatus != null) {
            result = printJobRepository.findByStatusOrderByCreatedAtDesc(safeStatus, pageable);
        } else if (!safeChannel.isBlank()) {
            result = printJobRepository.findByPrintChannelIgnoreCaseOrderByCreatedAtDesc(safeChannel, pageable);
        } else {
            result = printJobRepository.findAll(pageable);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getContent());
        body.put("total", result.getTotalElements());
        body.put("page", safePage);
        body.put("size", safeSize);
        return body;
    }

    private PrintJobStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return PrintJobStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知打印状态：" + status);
        }
    }

    @Transactional
    public PrintJobEntity createForTask(FactoryDtos.PrintRequest req) {
        FactoryDtos.PrintRequest request = req == null ? new FactoryDtos.PrintRequest() : req;
        if (request.taskNo == null || request.taskNo.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "任务号不能为空");
        ReplenishmentTaskEntity task = taskRepository.findByTaskNo(request.taskNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在：" + request.taskNo));
        dataScopeService.requireAccessFactoryArea(task.getFactory(), task.getDeliveryArea());
        String operator = OperatorResolver.currentOperator();
        String channel = normalizeChannel(request.printChannel);
        PrintJobEntity job = buildJob(task, request.printType, request.printerName, operator);
        job.setPrintChannel(channel);
        if ("BROWSER".equals(channel)) {
            // 浏览器打印：由前端本机打印机直接出纸，后端不入代理队列，直接标记为已打印完成。
            job.setStatus(PrintJobStatus.PRINTED);
            job.setPrintedAt(LocalDateTime.now());
            job.setResponsePayload("{\"channel\":\"BROWSER\",\"note\":\"浏览器本机打印\"}");
        } else {
            submitToPrinter(job);
        }
        job = printJobRepository.save(job);
        task.setPrintJobNo(job.getPrintJobNo());
        task.setPrintGenerated(true);
        syncTaskPrintState(task, job);
        taskRepository.save(task);
        String auditMsg = "BROWSER".equals(channel) ? "浏览器打印已完成：" + job.getPrintJobNo() : "已提交本地代理打印队列：" + job.getPrintJobNo();
        auditService.print(firstNonBlank(task.getSourceLabelCode(), request.labelCode), "BROWSER".equals(channel) ? "BROWSER_PRINT" : "SUBMIT_PRINT_JOB", operator, job.getPrinterName(), true, auditMsg);
        pushService.publish("printJobs", job);
        return job;
    }

    @Transactional
    public PrintJobEntity createForTask(ReplenishmentTaskEntity task, String operator) {
        if (task == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "任务不能为空");
        if (Boolean.TRUE.equals(task.getPrintGenerated()) && task.getPrintJobNo() != null) {
            return printJobRepository.findByPrintJobNo(task.getPrintJobNo()).orElse(null);
        }
        PrintJobEntity job = buildJob(task, properties.getDefaultPrintType(), properties.getDefaultPrinterName(), operator);
        job.setPrintChannel("AGENT");
        submitToPrinter(job);
        job = printJobRepository.save(job);
        task.setPrintJobNo(job.getPrintJobNo());
        task.setPrintGenerated(true);
        syncTaskPrintState(task, job);
        taskRepository.save(task);
        auditService.print(task.getSourceLabelCode(), "AUTO_SUBMIT_OUTBOUND_LABEL", operator, job.getPrinterName(), true, "仓库接单后已提交真实打印服务：" + job.getPrintJobNo());
        pushService.publish("printJobs", job);
        return job;
    }


    /**
     * 定时自动打印：为未打印任务提交标签打印。已打印则跳过返回 null。
     * 打印机优先用传入值，其次系统默认打印机；两者都为空则跳过（避免生产环境误打）。
     */
    @Transactional
    public PrintJobEntity autoPrintForTask(ReplenishmentTaskEntity task, String printerName, String printType) {
        if (task == null) return null;
        if (Boolean.TRUE.equals(task.getPrintGenerated()) && task.getPrintJobNo() != null) return null;
        String printer = firstNonBlank(printerName, properties.getDefaultPrinterName());
        if (printer == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "定时自动打印未配置打印机名称，请在系统参数 print.auto.printer-name 或默认打印机中填写");
        }
        PrintJobEntity job = buildJob(task, firstNonBlank(printType, "WAREHOUSE_BARCODE_LABEL"), printer, OperatorResolver.systemOperator());
        job.setPrintChannel("AGENT");
        submitToPrinter(job);
        job = printJobRepository.save(job);
        task.setPrintJobNo(job.getPrintJobNo());
        task.setPrintGenerated(true);
        syncTaskPrintState(task, job);
        taskRepository.save(task);
        auditService.print(task.getSourceLabelCode(), "AUTO_SCHEDULE_PRINT", OperatorResolver.systemOperator(), job.getPrinterName(), true, "定时自动打印已提交：" + job.getPrintJobNo());
        pushService.publish("printJobs", job);
        return job;
    }

    @Transactional
    public void cancelByTaskNo(String taskNo, String operator) {
        if (taskNo == null || taskNo.isBlank()) return;
        for (PrintJobEntity job : printJobRepository.findByTaskNo(taskNo)) {
            if (job.getStatus() == PrintJobStatus.CANCELLED) continue;
            String cancelPayload = "{\"printJobNo\":\"" + esc(job.getPrintJobNo()) + "\",\"taskNo\":\"" + esc(job.getTaskNo()) + "\",\"printerName\":\"" + esc(job.getPrinterName()) + "\",\"operator\":\"" + esc(firstNonBlank(operator, OperatorResolver.systemOperator())) + "\"}";
            if (job.getStatus() == PrintJobStatus.SENT || job.getStatus() == PrintJobStatus.PRINTED) {
                String response = externalHttpClient.postJson(
                        properties.getPrintCancelUrl(),
                        cancelPayload,
                        properties.getPrintAuthHeader(),
                        properties.getPrintApiKey(),
                        properties.getExternalCallTimeoutMs(),
                        "打印取消"
                );
                job.setResponsePayload(response);
            }
            job.setStatus(PrintJobStatus.CANCELLED);
            job.setLastError("任务取消，打印作业作废，操作人=" + firstNonBlank(operator, OperatorResolver.systemOperator()));
            printJobRepository.save(job);
            syncTaskPrintState(job);
            auditService.print(job.getLabelCode(), "CANCEL_PRINT_JOB", operator, job.getPrinterName(), true, "任务取消已同步打印服务：" + job.getPrintJobNo());
            pushService.publish("printJobs", job);
        }
    }

    @Transactional
    public PrintJobEntity callback(FactoryDtos.PrintCallbackRequest req) {
        if (req == null || req.printJobNo == null || req.printJobNo.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "打印任务号不能为空");
        if (req.status == null || req.status.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "打印状态不能为空");
        PrintJobEntity job = printJobRepository.findByPrintJobNo(req.printJobNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "打印任务不存在：" + req.printJobNo));
        PrintJobStatus status;
        try {
            status = PrintJobStatus.valueOf(req.status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知打印状态：" + req.status);
        }
        job.setStatus(status);
        if (req.externalJobNo != null && !req.externalJobNo.isBlank()) job.setExternalJobNo(req.externalJobNo.trim());
        if (status == PrintJobStatus.PRINTED) job.setPrintedAt(LocalDateTime.now());
        if (status == PrintJobStatus.FAILED) job.setLastError(req.message);
        job.setResponsePayload(firstNonBlank(req.rawPayload, "{\"status\":\"" + status.name() + "\",\"message\":\"" + esc(req.message) + "\"}"));
        printJobRepository.save(job);
        syncTaskPrintState(job);
        auditService.iface("PRINT_CALLBACK", "IN", req.printJobNo, job.getResponsePayload(), status != PrintJobStatus.FAILED, firstNonBlank(req.message, "打印状态回调"));
        pushService.publish("printJobs", job);
        return job;
    }

    private PrintJobEntity buildJob(ReplenishmentTaskEntity task, String printType, String printerName, String operator) {
        PrintJobEntity job = new PrintJobEntity();
        job.setPrintJobNo(IdGenerator.id("PRN"));
        job.setTaskNo(task.getTaskNo());
        job.setFactory(task.getFactory());
        job.setDeliveryArea(task.getDeliveryArea());
        job.setLabelCode(task.getSourceLabelCode());
        job.setPrintType(firstNonBlank(printType, properties.getDefaultPrintType(), "OUTBOUND_LABEL"));
        job.setPrinterName(firstNonBlank(printerName, properties.getDefaultPrinterName()));
        job.setOperator(firstNonBlank(operator, OperatorResolver.systemOperator()));
        job.setPayload(payload(job, task));
        job.setZplContent(zpl(task));
        job.setStatus(PrintJobStatus.RENDERED);
        return job;
    }

    private void submitToPrinter(PrintJobEntity job) {
        if (job.getPrinterName() == null || job.getPrinterName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "打印机名称不能为空，生产环境禁止使用未指定打印机");
        }
        // 拉模式：不主动推送，作业停在 RENDERED 状态，等待本地打印代理轮询领取。
        if (properties.isPrintPullMode()) {
            job.setStatus(PrintJobStatus.RENDERED);
            return;
        }
        // 推模式：后端主动把作业 HTTP 推送到外部打印服务。
        String response = externalHttpClient.postJson(
                properties.getPrintSubmitUrl(),
                job.getPayload(),
                properties.getPrintAuthHeader(),
                properties.getPrintApiKey(),
                properties.getExternalCallTimeoutMs(),
                "打印服务"
        );
        job.setStatus(PrintJobStatus.SENT);
        job.setSentAt(LocalDateTime.now());
        job.setResponsePayload(response);
    }

    /**
     * 本地打印代理领取一批待打印作业（拉模式）。原子地把 RENDERED（及超时未回传的 SENT）
     * 作业置为 SENT 并返回其 ZPL 内容。printerName 传入时只领取该打印机的作业，为空则领取全部。
     */
    @Transactional
    public List<PrintJobEntity> claimForAgent(String printerName, int limit) {
        int max = limit <= 0 ? 10 : Math.min(limit, 50);
        long staleSeconds = properties.getPrintClaimStaleSeconds() <= 0 ? 120 : properties.getPrintClaimStaleSeconds();
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(staleSeconds);
        String wantPrinter = firstNonBlank(printerName);
        List<PrintJobEntity> claimed = new ArrayList<>();
        for (PrintJobEntity job : printJobRepository.findTop50ByStatusInOrderByCreatedAtAsc(List.of(PrintJobStatus.RENDERED, PrintJobStatus.SENT))) {
            if (claimed.size() >= max) break;
            if (job.getStatus() == PrintJobStatus.SENT) {
                // 只重发那些领取后长时间没有回传结果的作业（代理可能崩溃/断网）。
                if (job.getSentAt() != null && job.getSentAt().isAfter(staleBefore)) continue;
            }
            if (wantPrinter != null && job.getPrinterName() != null && !job.getPrinterName().isBlank()
                    && !wantPrinter.equalsIgnoreCase(job.getPrinterName().trim())) {
                continue;
            }
            job.setStatus(PrintJobStatus.SENT);
            job.setSentAt(LocalDateTime.now());
            printJobRepository.save(job);
            syncTaskPrintState(job);
            claimed.add(job);
        }
        return claimed;
    }

    private String payload(PrintJobEntity job, ReplenishmentTaskEntity t) {
        boolean urgent = t.getPriority() == PriorityLevel.URGENT || "URGENT".equalsIgnoreCase(firstNonBlank(t.getDeliveryMode(), "")) || "SPARE".equalsIgnoreCase(firstNonBlank(t.getLabelUsageType(), ""));
        return "{\"printJobNo\":\"" + esc(job.getPrintJobNo()) + "\",\"factory\":\"" + esc(displayFactory(job.getFactory())) + "\",\"taskNo\":\"" + esc(t.getTaskNo()) + "\",\"labelCode\":\"" + esc(t.getSourceLabelCode()) + "\",\"printType\":\"" + esc(job.getPrintType()) + "\",\"printerName\":\"" + esc(job.getPrinterName()) + "\",\"barcode\":\"" + esc(t.getWarehouseCode()) + "\",\"warehouseCode\":\"" + esc(t.getWarehouseCode()) + "\",\"materialCode\":\"" + esc(t.getMaterialCode()) + "\",\"materialName\":\"" + esc(firstNonBlank(t.getMaterialName(), t.getMaterialCode(), "")) + "\",\"materialImageUrl\":\"" + esc(t.getMaterialImageUrl()) + "\",\"boxSize\":\"" + esc(t.getBoxSize()) + "\",\"qty\":\"" + t.getRequestQty() + "\",\"from\":\"" + esc(firstNonBlank(t.getWarehouseAddress(), t.getWarehouseLocation(), "")) + "\",\"to\":\"" + esc(firstNonBlank(t.getSendStationAddress(), t.getDeliveryAddress(), t.getStationCode())) + "\",\"deliveryArea\":\"" + esc(t.getDeliveryArea()) + "\",\"deliveryMode\":\"" + (urgent ? "URGENT" : "NORMAL") + "\",\"usageType\":\"" + esc(firstNonBlank(t.getLabelUsageType(), urgent ? "SPARE" : "USE")) + "\",\"zpl\":\"" + esc(zpl(t)) + "\"}";
    }

    private String zpl(ReplenishmentTaskEntity t) {
        String to = firstNonBlank(t.getSendStationAddress(), t.getDeliveryAddress(), t.getStationName(), t.getStationCode(), "");
        String from = firstNonBlank(t.getWarehouseAddress(), t.getWarehouseLocation(), "");
        String barcode = firstNonBlank(t.getWarehouseCode(), t.getBarcodeValue(), "");
        String material = firstNonBlank(t.getMaterialCode(), t.getMaterialName(), "");
        String boxSize = firstNonBlank(t.getBoxSize(), "");
        String qty = t.getRequestQty() == null ? "" : t.getRequestQty().stripTrailingZeros().toPlainString();
        String deliveryArea = firstNonBlank(t.getDeliveryArea(), "");
        boolean urgent = t.getPriority() == PriorityLevel.URGENT || "URGENT".equalsIgnoreCase(firstNonBlank(t.getDeliveryMode(), "")) || "SPARE".equalsIgnoreCase(firstNonBlank(t.getLabelUsageType(), ""));

        // 实际画布点数 = 物理尺寸(mm) × dpi/25.4。所有坐标按参考画布(640×400)等比缩放，
        // 从而适配任意 dpi(203/300...)与标签尺寸，避免 300dpi 打印机上排版被放大溢出。
        // 横版 80×50mm@203dpi ≈ 640×400 点：上=用途条+条码，中=三栏(物料/仓库/工位)，下=四栏(盒子/数量/配送区域/任务号)。
        LabelGeometry g = labelGeometry();
        double sx = g.widthDots / REF_W;   // 横向缩放比
        double sy = g.heightDots / REF_H;  // 纵向缩放比

        StringBuilder zpl = new StringBuilder();
        zpl.append("^XA\n^CI28\n")
           .append("^PW").append(g.widthDots).append("\n")
           .append("^LL").append(g.heightDots).append("\n^LH0,0\n");
        // 外框
        box(zpl, 8, 8, 624, 384, 3, sx, sy);

        // ===== 顶部带(y 8..140)：左=用途黑底反白药丸，右=条码+数字 =====
        box(zpl, 24, 36, 268, 68, 68, sx, sy); // 用途药丸(填充黑块)
        text(zpl, 40, 52, 34, urgent ? "紧急配送(备用)" : "正常配送(使用)", sx, sy, true);
        // 条码区（仓库代号），下方带明文数字，便于扫码与人工核对
        int byWidth = Math.max(2, (int) Math.round(2 * sx));
        int bcHeight = scale(74, sy);
        zpl.append("^FO").append(scale(320, sx)).append(",").append(scale(26, sy))
           .append("^BY").append(byWidth).append(",2.5,").append(bcHeight)
           .append("^BCN,").append(bcHeight).append(",Y,N,N^FD").append(z(barcode)).append("^FS\n");

        // ===== 中部带(y 140..262)：三栏，栏内小字标签在上、大字值在下(超长折2行) =====
        box(zpl, 8, 140, 624, 0, 2, sx, sy);       // 上分隔线
        box(zpl, 216, 140, 0, 122, 2, sx, sy);     // 竖分隔
        box(zpl, 424, 140, 0, 122, 2, sx, sy);
        appendCol(zpl, 20, 152, "物料名称", material, 188, 40, 2, sx, sy);
        appendCol(zpl, 228, 152, "仓库地址", from, 188, 40, 2, sx, sy);
        appendCol(zpl, 436, 152, "发送工位地址", to, 188, 36, 2, sx, sy);

        // ===== 底部带(y 262..392)：四栏 盒子大小/数量/配送区域/任务号 =====
        box(zpl, 8, 262, 624, 0, 2, sx, sy);       // 上分隔线
        box(zpl, 164, 262, 0, 130, 2, sx, sy);     // 竖分隔
        box(zpl, 320, 262, 0, 130, 2, sx, sy);
        box(zpl, 476, 262, 0, 130, 2, sx, sy);
        appendCol(zpl, 20, 276, "盒子大小", boxSize, 140, 44, 1, sx, sy);
        appendCol(zpl, 176, 276, "数量", qty, 140, 44, 1, sx, sy);
        appendCol(zpl, 332, 276, "配送区域", deliveryArea, 140, 32, 2, sx, sy);
        appendCol(zpl, 488, 276, "任务号", firstNonBlank(t.getTaskNo(), ""), 140, 20, 3, sx, sy);

        zpl.append("^XZ");
        return zpl.toString();
    }

    /**
     * 输出一栏字段：顶部小字标签 + 下方大字值；值超长时按 ^FB 自动折到最多 maxLines 行。
     * 坐标/字号按缩放比换算，x/y 为参考画布(640×400)坐标。
     */
    private void appendCol(StringBuilder zpl, int x, int y, String label, String value, int fbWidth, int valueFont, int maxLines, double sx, double sy) {
        text(zpl, x, y, 22, z(label), sx, sy, false);
        int fx = scale(x, sx);
        int fy = scale(y + 30, sy);
        int font = scale(valueFont, sy);
        int fb = scale(fbWidth, sx);
        int lineGap = Math.max(0, scale(3, sy));
        zpl.append("^FO").append(fx).append(",").append(fy)
           .append("^A0N,").append(font).append(",").append(font)
           .append("^FB").append(fb).append(",").append(Math.max(1, maxLines)).append(",").append(lineGap).append(",L,0^FD").append(z(value)).append("^FS\n");
    }

    /** 输出一段文字，坐标与字号按缩放比换算；reversed=true 时反白(^FR)用于黑底紧急条。 */
    private void text(StringBuilder zpl, int x, int y, int fontRef, String content, double sx, double sy, boolean reversed) {
        int font = scale(fontRef, sy);
        zpl.append("^FO").append(scale(x, sx)).append(",").append(scale(y, sy))
           .append("^A0N,").append(font).append(",").append(font);
        if (reversed) zpl.append("^FR");
        zpl.append("^FD").append(content).append("^FS\n");
    }

    /**
     * 输出矩形/线条(^GB)，宽高与线宽都按缩放比换算。
     * ZPL 中当 thickness>=min(w,h) 时 ^GB 会被填满(黑块)；缩放后必须保持这一关系，
     * 否则黑底用途条会出现中缝、或普通边框/分隔线被误填成实心块。
     */
    private void box(StringBuilder zpl, int x, int y, int w, int h, int thickness, double sx, double sy) {
        boolean filled = w > 0 && h > 0 && thickness >= Math.min(w, h);
        int gw = w == 0 ? 0 : scale(w, sx);
        int gh = h == 0 ? 0 : scale(h, sy);
        int th;
        if (filled) {
            // 保持填充：线宽取缩放后宽高的较大者，确保 >=min(gw,gh) 而填满
            th = Math.max(gw, gh);
        } else {
            // 普通边框/直线：线宽按横纵缩放较小者换算，至少 1 点
            th = Math.max(1, (int) Math.round(thickness * Math.min(sx, sy)));
        }
        zpl.append("^FO").append(scale(x, sx)).append(",").append(scale(y, sy))
           .append("^GB").append(gw).append(",").append(gh).append(",").append(th).append("^FS\n");
    }

    private int scale(int refValue, double ratio) {
        return Math.max(0, (int) Math.round(refValue * ratio));
    }

    /** 标签几何：由系统参数 dpi + 宽高(mm) 计算的实际点数。读取失败时回退 203dpi/35×95mm(=280×760)。 */
    private LabelGeometry labelGeometry() {
        int dpi = readIntConfig(CFG_DPI, 203, 100, 600);
        double widthMm = readDoubleConfig(CFG_WIDTH_MM, 80.0, 5.0, 300.0);
        double heightMm = readDoubleConfig(CFG_HEIGHT_MM, 50.0, 5.0, 500.0);
        int wDots = (int) Math.round(widthMm * dpi / 25.4);
        int hDots = (int) Math.round(heightMm * dpi / 25.4);
        return new LabelGeometry(Math.max(1, wDots), Math.max(1, hDots));
    }

    private int readIntConfig(String key, int def, int min, int max) {
        try {
            String v = systemConfigRepository.findByConfigKey(key).map(SystemConfigEntity::getConfigValue).orElse(null);
            if (v == null || v.isBlank()) return def;
            int n = Integer.parseInt(v.trim());
            return Math.min(max, Math.max(min, n));
        } catch (RuntimeException e) {
            return def;
        }
    }

    private double readDoubleConfig(String key, double def, double min, double max) {
        try {
            String v = systemConfigRepository.findByConfigKey(key).map(SystemConfigEntity::getConfigValue).orElse(null);
            if (v == null || v.isBlank()) return def;
            double n = Double.parseDouble(v.trim());
            return Math.min(max, Math.max(min, n));
        } catch (RuntimeException e) {
            return def;
        }
    }

    private record LabelGeometry(int widthDots, int heightDots) {}

    private String displayFactory(String factory) {
        if (factory == null) return "未维护工厂";
        String value = factory.trim();
        return "弋江".equals(value) || "三山".equals(value) ? value : "未维护工厂";
    }

    private String esc(String v) { return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }

    private void syncTaskPrintState(PrintJobEntity job) {
        if (job == null || job.getTaskNo() == null || job.getTaskNo().isBlank()) return;
        taskRepository.findByTaskNo(job.getTaskNo()).ifPresent(task -> {
            syncTaskPrintState(task, job);
            taskRepository.save(task);
        });
    }

    private void syncTaskPrintState(ReplenishmentTaskEntity task, PrintJobEntity job) {
        if (task == null || job == null) return;
        task.setPrintJobNo(job.getPrintJobNo());
        task.setPrintGenerated(job.getStatus() != PrintJobStatus.CANCELLED);
        task.setPrintStatus(job.getStatus() == null ? null : job.getStatus().name());
        task.setPrintChannel(job.getPrintChannel());
        task.setPrintedAt(job.getPrintedAt());
        task.setPrintLastError(job.getLastError());
    }

    private String normalizeChannel(String v) {
        String s = firstNonBlank(v);
        if (s == null) return "AGENT";
        s = s.trim().toUpperCase(Locale.ROOT);
        return "BROWSER".equals(s) ? "BROWSER" : "AGENT";
    }

    private String z(String v) { return v == null ? "" : v.replace("^", "").replace("~", ""); }
    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v.trim(); return null; }
}
