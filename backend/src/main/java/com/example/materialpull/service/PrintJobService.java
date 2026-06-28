package com.example.materialpull.service;

import com.example.materialpull.common.*;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    public List<PrintJobEntity> list(String status) {
        if (status == null || status.isBlank()) return printJobRepository.findTop1000ByOrderByCreatedAtDesc();
        PrintJobStatus s;
        try {
            s = PrintJobStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知打印状态：" + status);
        }
        return printJobRepository.findTop1000ByStatusOrderByCreatedAtDesc(s);
    }

    @Transactional
    public PrintJobEntity createForTask(FactoryDtos.PrintRequest req) {
        FactoryDtos.PrintRequest request = req == null ? new FactoryDtos.PrintRequest() : req;
        if (request.taskNo == null || request.taskNo.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "任务号不能为空");
        ReplenishmentTaskEntity task = taskRepository.findByTaskNo(request.taskNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在：" + request.taskNo));
        String operator = OperatorResolver.currentOperator();
        PrintJobEntity job = buildJob(task, request.printType, request.printerName, operator);
        submitToPrinter(job);
        job = printJobRepository.save(job);
        task.setPrintJobNo(job.getPrintJobNo());
        task.setPrintGenerated(true);
        taskRepository.save(task);
        auditService.print(firstNonBlank(task.getSourceLabelCode(), request.labelCode), "SUBMIT_PRINT_JOB", operator, job.getPrinterName(), true, "已提交真实打印服务：" + job.getPrintJobNo());
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
        submitToPrinter(job);
        job = printJobRepository.save(job);
        task.setPrintJobNo(job.getPrintJobNo());
        task.setPrintGenerated(true);
        auditService.print(task.getSourceLabelCode(), "AUTO_SUBMIT_OUTBOUND_LABEL", operator, job.getPrinterName(), true, "仓库接单后已提交真实打印服务：" + job.getPrintJobNo());
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
        auditService.iface("PRINT_CALLBACK", "IN", req.printJobNo, job.getResponsePayload(), status != PrintJobStatus.FAILED, firstNonBlank(req.message, "打印状态回调"));
        pushService.publish("printJobs", job);
        return job;
    }

    private PrintJobEntity buildJob(ReplenishmentTaskEntity task, String printType, String printerName, String operator) {
        PrintJobEntity job = new PrintJobEntity();
        job.setPrintJobNo(IdGenerator.id("PRN"));
        job.setTaskNo(task.getTaskNo());
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

    private String payload(PrintJobEntity job, ReplenishmentTaskEntity t) {
        return "{\"printJobNo\":\"" + esc(job.getPrintJobNo()) + "\",\"taskNo\":\"" + esc(t.getTaskNo()) + "\",\"labelCode\":\"" + esc(t.getSourceLabelCode()) + "\",\"printType\":\"" + esc(job.getPrintType()) + "\",\"printerName\":\"" + esc(job.getPrinterName()) + "\",\"barcode\":\"" + esc(t.getWarehouseCode()) + "\",\"warehouseCode\":\"" + esc(t.getWarehouseCode()) + "\",\"materialCode\":\"" + esc(t.getMaterialCode()) + "\",\"materialName\":\"" + esc(firstNonBlank(t.getMaterialName(), t.getMaterialCode(), "")) + "\",\"materialImageUrl\":\"" + esc(t.getMaterialImageUrl()) + "\",\"boxSize\":\"" + esc(t.getBoxSize()) + "\",\"qty\":\"" + t.getRequestQty() + "\",\"from\":\"" + esc(firstNonBlank(t.getWarehouseAddress(), t.getWarehouseLocation(), "")) + "\",\"to\":\"" + esc(firstNonBlank(t.getSendStationAddress(), t.getDeliveryAddress(), t.getStationCode())) + "\",\"delivererEmployeeNo\":\"" + esc(t.getDelivererEmployeeNo()) + "\",\"zpl\":\"" + esc(zpl(t)) + "\"}";
    }

    private String zpl(ReplenishmentTaskEntity t) {
        String to = firstNonBlank(t.getSendStationAddress(), t.getDeliveryAddress(), t.getStationName(), t.getStationCode(), "");
        String from = firstNonBlank(t.getWarehouseAddress(), t.getWarehouseLocation(), "");
        String barcode = firstNonBlank(t.getWarehouseCode(), t.getBarcodeValue(), "");
        String material = firstNonBlank(t.getMaterialCode(), t.getMaterialName(), "");
        String boxSize = firstNonBlank(t.getBoxSize(), "");
        String qty = t.getRequestQty() == null ? "" : t.getRequestQty().stripTrailingZeros().toPlainString();
        String worker = firstNonBlank(t.getDelivererEmployeeNo(), "");
        return "^XA\n" +
                "^CI28\n" +
                "^PW1238\n" +
                "^LL1519\n" +
                "^LH0,0\n" +
                "^FO80,70^GB1080,1400,4^FS\n" +
                "^FO80,70^GB1080,300,4^FS\n" +
                "^FO120,95^BY4,3,170^BCN,170,Y,N,N^FD" + z(barcode) + "^FS\n" +
                "^FO80,370^GB1080,220,4^FS\n" +
                "^FO80,590^GB1080,220,4^FS\n" +
                "^FO80,810^GB1080,220,4^FS\n" +
                "^FO80,1030^GB1080,220,4^FS\n" +
                "^FO620,1030^GB0,220,4^FS\n" +
                "^FO80,1250^GB1080,220,4^FS\n" +
                "^FO105,440^A0N,54,54^FD物料名称^FS\n" +
                "^FO480,440^A0N,52,52^FD" + z(material) + "^FS\n" +
                "^FO105,660^A0N,54,54^FD仓库地址^FS\n" +
                "^FO420,660^A0N,48,48^FD" + z(from) + "^FS\n" +
                "^FO105,880^A0N,54,54^FD发送工位地址^FS\n" +
                "^FO500,880^A0N,44,44^FD" + z(to) + "^FS\n" +
                "^FO105,1100^A0N,54,54^FD盒子大小^FS\n" +
                "^FO380,1100^A0N,52,52^FD" + z(boxSize) + "^FS\n" +
                "^FO690,1100^A0N,54,54^FD数量^FS\n" +
                "^FO880,1100^A0N,52,52^FD" + z(qty) + "^FS\n" +
                "^FO105,1320^A0N,54,54^FD送料人工号^FS\n" +
                "^FO460,1320^A0N,52,52^FD" + z(worker) + "^FS\n" +
                "^FO80,1475^A0N,26,26^FD任务号:" + z(t.getTaskNo()) + "^FS\n" +
                "^XZ";
    }

    private String esc(String v) { return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }
    private String z(String v) { return v == null ? "" : v.replace("^", "").replace("~", ""); }
    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v.trim(); return null; }
}
