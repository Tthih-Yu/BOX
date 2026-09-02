package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.IdGenerator;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.WeeklyPlanStatus;
import com.example.materialpull.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 周计划 Excel 导入解析与持久化。
 *
 * 模板(以 `E0Y&T2Y 生产计划 WK40_V4` 为准)：
 *  - 标题行：解析周次(WKxx)与 Excel 版本(Vx)。
 *  - 两行表头：第一行固定9列(客户/生产工厂/项目/客户号/8D号/描述/标包/JPH/WH)后跟重复的“白/夜”；
 *    第二行是日期(月/日)，每个日期横跨白、夜两列(合并单元格)。
 *  - 数据行：到“合计”行为止；“合计”行与签字区(生产计划主管/生产主管/班长确认等)一律跳过。
 *  - 当天数量 = 白班 + 夜班；周计划数量 = 七天数量之和。
 *  - 8D号取单元格数值、按字符串校验为8位纯数字，避免科学计数法/前导零丢失。
 *
 * 周计划体量小(几百行)，用 DOM 方式读取即可，不需要 BOM 那种流式高速通道。
 */
@Service
@RequiredArgsConstructor
public class WeeklyPlanService {
    private final WeeklyPlanBatchRepository batchRepository;
    private final WeeklyPlanRowRepository rowRepository;
    private final WeeklyPlanShiftQtyRepository shiftRepository;
    private final DataScopeService dataScopeService;
    private final DataFormatter dataFormatter = new DataFormatter();

    /** 固定列数：客户/生产工厂/项目/客户号/8D号/描述/标包/JPH/WH。白/夜从第10列(下标9)开始。 */
    private static final int FIXED_COLS = 9;
    private static final int SHIFT_START_COL = 9;
    private static final Pattern CODE_8D = Pattern.compile("^\\d{8}$");
    private static final Pattern WEEK_PAT = Pattern.compile("WK\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern VER_PAT = Pattern.compile("V\\s*(\\d{1,3})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PAT = Pattern.compile("^(\\d{1,2})[/.\\-](\\d{1,2})$");
    /** 合计行/签字区关键词：命中则视为数据区结束或跳过。 */
    private static final String[] STOP_KEYWORDS = {"合计", "总计", "生产计划主管", "生产主管", "生产计划", "班长确认", "签字", "审核", "批准"};

    /**
     * 导入一份周计划 Excel。planYear 由上传者指定(模板日期只有月/日)。
     * 默认不覆盖：若检测到冲突且 confirmOverwrite=false，则抛出带冲突明细的异常，由前端二次确认。
     */
    @Transactional
    public ImportResult importPlan(MultipartFile file, Integer planYear, boolean confirmOverwrite, String operator) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件不能为空");
        if (planYear == null || planYear < 2000 || planYear > 2100) throw new BusinessException(ErrorCode.PARAM_ERROR, "请指定有效的计划年份");
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("");

        ParseResult parsed;
        try (InputStream in = new BufferedInputStream(file.getInputStream());
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "文件没有可读取的工作表");
            parsed = parseSheet(sheet, planYear);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "周计划文件解析失败：" + e.getMessage());
        }
        if (parsed.rows.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "未解析到任何有效计划行，请检查模板格式");
        String trustedFactory = resolveImportFactory(parsed);
        parsed.factory = trustedFactory;
        parsed.rows.forEach(row -> row.entity.setFactory(trustedFactory));

        // 冲突检测：同年份+周次+8D号已有其它批次的计划行。
        List<Map<String, Object>> conflicts = detectConflicts(parsed);
        if (!conflicts.isEmpty() && !confirmOverwrite) {
            ImportResult r = new ImportResult();
            r.needConfirm = true;
            r.weekNo = parsed.weekNo;
            r.planYear = planYear;
            r.excelVersion = parsed.excelVersion;
            r.parsedRows = parsed.rows.size();
            r.conflicts = conflicts;
            return r;
        }

        WeeklyPlanBatchEntity batch = new WeeklyPlanBatchEntity();
        batch.setBatchNo(IdGenerator.id("WPL"));
        batch.setPlanYear(planYear);
        batch.setWeekNo(parsed.weekNo);
        batch.setExcelVersion(parsed.excelVersion);
        batch.setTitleText(parsed.title);
        batch.setFactory(parsed.factory);
        batch.setFileName(fileName);
        batch.setOperator(operator == null || operator.isBlank() ? OperatorResolver.systemOperator() : operator.trim());
        batchRepository.save(batch);

        if (confirmOverwrite) supersedeConflicts(parsed);

        int success = 0;
        for (ParsedRow pr : parsed.rows) {
            WeeklyPlanRowEntity row = pr.entity;
            row.setBatchNo(batch.getBatchNo());
            rowRepository.save(row);
            for (WeeklyPlanShiftQtyEntity q : pr.shifts) {
                q.setRowId(row.getId());
                q.setBatchNo(batch.getBatchNo());
                shiftRepository.save(q);
            }
            success++;
        }
        batch.setTotalRows(parsed.rows.size());
        batch.setSuccessRows(success);
        batch.setFailedRows(0);
        batch.setStatus(WeeklyPlanStatus.SUCCESS);
        batch.setFinishedAt(LocalDateTime.now());
        batchRepository.save(batch);
        supersedeActiveBatches(batch);

        ImportResult r = new ImportResult();
        r.needConfirm = false;
        r.batch = batch;
        r.weekNo = parsed.weekNo;
        r.planYear = planYear;
        r.excelVersion = parsed.excelVersion;
        r.parsedRows = parsed.rows.size();
        r.conflicts = List.of();
        return r;
    }

    public List<WeeklyPlanBatchEntity> listBatches() {
        if (dataScopeService.isGlobalAdmin()) return batchRepository.findTop200ByOrderByIdDesc();
        return batchRepository.findTop200ByFactoryIgnoreCaseOrderByIdDesc(dataScopeService.currentFactory());
    }

    /**
     * 生成空白周计划模板 xlsx：标题(含 WKxx)、两行表头(固定9列 + 白/夜×7)、日期行留空由计划员自填、
     * 若干空数据行、末尾合计行。与解析逻辑的列位约定保持一致。
     */
    public byte[] buildTemplate(int weekNo, String factoryTag) {
        return buildTemplate(new TemplateOptions(weekNo, factoryTag, null, null, null, null, null, null));
    }

    /** 模板生成参数：起始日自动推算七天日期；客户等字段用于预填数据行。 */
    public record TemplateOptions(Integer weekNo, String factoryTag, LocalDate startDate,
                                  String customer, String factory, String project, String customerNo,
                                  Integer blankRows) {}

    public byte[] buildTemplate(TemplateOptions opt) {
        int weekNo = opt.weekNo() == null ? 1 : opt.weekNo();
        LocalDate start = opt.startDate();
        // 起始日给定时按周次自动校正标题里的 WKxx，保证标题与日期一致。
        if (start != null) weekNo = start.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        String tag = (opt.factoryTag() == null || opt.factoryTag().isBlank()) ? "E0Y&T2Y" : opt.factoryTag().trim();
        String[] fixedHeaders = {"客户", "生产工厂", "项目", "客户号", "8D号", "描述", "标包", "JPH", "WH"};
        int days = 7;
        int blankRows = opt.blankRows() == null || opt.blankRows() < 1 ? 10 : Math.min(opt.blankRows(), 200);
        int totalCols = FIXED_COLS + days * 2;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("周计划");
            CellStyle center = wb.createCellStyle();
            center.setAlignment(HorizontalAlignment.CENTER);
            center.setBorderBottom(BorderStyle.THIN);
            center.setBorderTop(BorderStyle.THIN);
            center.setBorderLeft(BorderStyle.THIN);
            center.setBorderRight(BorderStyle.THIN);

            // 标题行
            Row title = sheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue(tag + " 生产计划 WK" + weekNo + "_V1");
            titleCell.setCellStyle(center);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, totalCols - 1));

            // 表头第一行：固定9列 + 每天“白/夜”
            Row h1 = sheet.createRow(1);
            for (int i = 0; i < fixedHeaders.length; i++) { Cell c = h1.createCell(i); c.setCellValue(fixedHeaders[i]); c.setCellStyle(center); }
            for (int d = 0; d < days; d++) {
                int base = SHIFT_START_COL + d * 2;
                Cell day = h1.createCell(base); day.setCellValue("白"); day.setCellStyle(center);
                Cell night = h1.createCell(base + 1); night.setCellValue("夜"); night.setCellStyle(center);
            }

            // 表头第二行：日期。给了起始日就按“月/日”自动填七天(每个日期合并白/夜两列)，否则留空由计划员自填。
            Row h2 = sheet.createRow(2);
            for (int i = 0; i < FIXED_COLS; i++) { Cell c = h2.createCell(i); c.setCellStyle(center); }
            for (int d = 0; d < days; d++) {
                int base = SHIFT_START_COL + d * 2;
                Cell c = h2.createCell(base);
                if (start != null) c.setCellValue(start.plusDays(d).getMonthValue() + "/" + start.plusDays(d).getDayOfMonth());
                c.setCellStyle(center);
                h2.createCell(base + 1).setCellStyle(center);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, base, base + 1));
            }

            // 数据行：前4列(客户/生产工厂/项目/客户号)按需预填，其余留空
            for (int r = 3; r < 3 + blankRows; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < totalCols; c++) row.createCell(c).setCellStyle(center);
                setIfPresent(row, 0, opt.customer());
                setIfPresent(row, 1, opt.factory());
                setIfPresent(row, 2, opt.project());
                setIfPresent(row, 3, opt.customerNo());
            }
            // 合计行(解析时遇到即停止)
            Row totalRow = sheet.createRow(3 + blankRows);
            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("合计");
            totalLabel.setCellStyle(center);

            for (int c = 0; c < totalCols; c++) sheet.setColumnWidth(c, 3200);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成周计划模板失败：" + e.getMessage());
        }
    }

    /** 删除批次及其计划行与班次数量。 */
    @Transactional
    public void deleteBatch(String batchNo) {
        WeeklyPlanBatchEntity batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "批次不存在：" + batchNo));
        dataScopeService.requireAccessFactoryOnly(batch.getFactory());
        shiftRepository.deleteByBatchNo(batchNo);
        rowRepository.deleteByBatchNo(batchNo);
        batchRepository.delete(batch);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<WeeklyPlanRowEntity> rows(String batchNo, org.springframework.data.domain.Pageable pageable) {
        WeeklyPlanBatchEntity batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "批次不存在：" + batchNo));
        dataScopeService.requireAccessFactoryOnly(batch.getFactory());
        return rowRepository.findByBatchNo(batchNo, pageable);
    }

    private String resolveImportFactory(ParseResult parsed) {
        Set<String> fileFactories = parsed.rows.stream().map(row -> blankToNull(row.entity.getFactory()))
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (dataScopeService.isGlobalAdmin()) {
            if (fileFactories.size() != 1) throw new BusinessException(ErrorCode.PARAM_ERROR, "ADMIN 导入必须在文件生产工厂列明确且统一指定一个工厂");
            return fileFactories.iterator().next();
        }
        String trusted = dataScopeService.currentFactory();
        if (!fileFactories.isEmpty() && (fileFactories.size() != 1 || !trusted.equalsIgnoreCase(fileFactories.iterator().next()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "周计划文件工厂与当前账号工厂不一致");
        }
        return trusted;
    }

    private void supersedeActiveBatches(WeeklyPlanBatchEntity current) {
        for (WeeklyPlanBatchEntity old : batchRepository.findByPlanYearAndWeekNoAndFactoryIgnoreCaseAndStatus(
                current.getPlanYear(), current.getWeekNo(), current.getFactory(), WeeklyPlanStatus.SUCCESS)) {
            if (Objects.equals(old.getId(), current.getId()) || Objects.equals(old.getBatchNo(), current.getBatchNo())) continue;
            old.setStatus(WeeklyPlanStatus.FAILED);
            old.setRemark("已被同工厂同年度周次的新批次 " + current.getBatchNo() + " 替代");
            old.setFinishedAt(LocalDateTime.now());
            batchRepository.save(old);
        }
    }

    /** 导入结果：needConfirm=true 时为冲突待确认(未落库)，否则 batch 为已保存批次。 */
    public static class ImportResult {
        public boolean needConfirm;
        public WeeklyPlanBatchEntity batch;
        public Integer planYear;
        public Integer weekNo;
        public String excelVersion;
        public int parsedRows;
        public List<Map<String, Object>> conflicts = new ArrayList<>();
    }

    /**
     * 解析工作表：定位标题行、两行表头、日期列，再逐行读数据到“合计/签字区”为止。
     */
    private ParseResult parseSheet(Sheet sheet, int planYear) {
        ParseResult result = new ParseResult();
        int first = sheet.getFirstRowNum();
        int last = sheet.getLastRowNum();

        // 1) 标题行：含“生产计划”或能匹配 WKxx 的首个非空行。
        int titleRowIdx = -1;
        for (int i = first; i <= last; i++) {
            String line = rowText(sheet.getRow(i));
            if (line.isBlank()) continue;
            if (line.contains("生产计划") || WEEK_PAT.matcher(line).find()) { titleRowIdx = i; result.title = line.trim(); break; }
        }
        if (titleRowIdx < 0) throw new BusinessException(ErrorCode.PARAM_ERROR, "未找到标题行(应含“生产计划”或 WKxx)");
        Matcher wk = WEEK_PAT.matcher(result.title);
        if (!wk.find()) throw new BusinessException(ErrorCode.PARAM_ERROR, "标题里未找到周次 WKxx：" + result.title);
        result.weekNo = Integer.parseInt(wk.group(1));
        Matcher ver = VER_PAT.matcher(result.title);
        result.excelVersion = ver.find() ? ("V" + ver.group(1)) : null;

        // 2) 第一行表头(含“8D号”)与其下一行(日期行)。
        int headerRowIdx = -1;
        for (int i = titleRowIdx + 1; i <= last; i++) {
            String line = rowText(sheet.getRow(i)).replace(" ", "");
            if (line.contains("8D") || (line.contains("客户") && line.contains("描述"))) { headerRowIdx = i; break; }
        }
        if (headerRowIdx < 0 || headerRowIdx + 1 > last) throw new BusinessException(ErrorCode.PARAM_ERROR, "未找到两行表头(客户…8D号…白/夜 与 日期行)");
        int dateRowIdx = headerRowIdx + 1;

        // 3) 日期列 -> LocalDate。日期行里每个“月/日”占一列，其右邻列为同一天的“夜”。
        List<DateCol> dateCols = resolveDateCols(sheet.getRow(dateRowIdx), planYear, result.weekNo);
        if (dateCols.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "日期行未解析到有效日期(应为 月/日 格式)");
        result.factory = null;

        // 4) 数据行：dateRow 之后到“合计/签字区”为止。
        for (int i = dateRowIdx + 1; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String code = rawCellString(row, 4); // 第5列(下标4)=8D号
            String firstCell = rawCellString(row, 0);
            String joined = rowText(row);
            if (isStopRow(joined)) break;                 // 合计/签字区：结束
            if (joined.isBlank()) continue;                // 空行跳过
            if (!CODE_8D.matcher(code.trim()).matches()) continue; // 非8D号数据行(小计等)跳过

            ParsedRow pr = buildRow(row, code.trim(), planYear, result.weekNo, dateCols);
            if (result.factory == null && pr.entity.getFactory() != null) result.factory = pr.entity.getFactory();
            result.rows.add(pr);
        }
        return result;
    }

    /** 日期行 -> (列下标, 日期)。每个日期单元格代表“白班列”，其右邻列为“夜班列”。 */
    private List<DateCol> resolveDateCols(Row dateRow, int planYear, int weekNo) {
        List<DateCol> cols = new ArrayList<>();
        if (dateRow == null) return cols;
        int lastCell = dateRow.getLastCellNum();
        for (int c = SHIFT_START_COL; c < lastCell; c++) {
            String v = rawCellString(dateRow, c).trim();
            Matcher m = DATE_PAT.matcher(v);
            if (!m.matches()) continue;
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            int year = planYear;
            // 跨年周：标题周次在年初(<=2)但日期是12月 -> 归上一年；周次在年末但日期是1月 -> 归下一年。
            if (weekNo <= 2 && month == 12) year = planYear - 1;
            else if (weekNo >= 50 && month == 1) year = planYear + 1;
            try {
                cols.add(new DateCol(c, LocalDate.of(year, month, day)));
            } catch (Exception ignore) { /* 非法日期跳过 */ }
        }
        return cols;
    }

    /** 构建一条计划行及其班次数量：当天=白(dateCol)+夜(dateCol+1)，周量=各天之和。 */
    private ParsedRow buildRow(Row row, String code, int planYear, int weekNo, List<DateCol> dateCols) {
        WeeklyPlanRowEntity e = new WeeklyPlanRowEntity();
        e.setPlanYear(planYear);
        e.setWeekNo(weekNo);
        e.setCustomer(blankToNull(rawCellString(row, 0)));
        e.setFactory(blankToNull(rawCellString(row, 1)));
        e.setProject(blankToNull(rawCellString(row, 2)));
        e.setCustomerNo(blankToNull(rawCellString(row, 3)));
        e.setProductCode(code);
        e.setDescription(blankToNull(rawCellString(row, 5)));
        e.setPackageQty(numOrNull(rawCellString(row, 6)));
        e.setJph(numOrNull(rawCellString(row, 7)));
        e.setWh(numOrNull(rawCellString(row, 8)));

        List<WeeklyPlanShiftQtyEntity> shifts = new ArrayList<>();
        BigDecimal weekTotal = BigDecimal.ZERO;
        for (DateCol dc : dateCols) {
            BigDecimal dayQty = numZero(rawCellString(row, dc.col));
            BigDecimal nightQty = numZero(rawCellString(row, dc.col + 1));
            weekTotal = weekTotal.add(dayQty).add(nightQty);
            shifts.add(shift(dc.date, "DAY", dayQty));
            shifts.add(shift(dc.date, "NIGHT", nightQty));
        }
        e.setWeekQty(weekTotal);
        return new ParsedRow(e, shifts);
    }

    private WeeklyPlanShiftQtyEntity shift(LocalDate date, String shift, BigDecimal qty) {
        WeeklyPlanShiftQtyEntity q = new WeeklyPlanShiftQtyEntity();
        q.setPlanDate(date);
        q.setShift(shift);
        q.setQty(qty == null ? BigDecimal.ZERO : qty);
        return q;
    }

    /**
     * 冲突：同年份+周次+工厂+8D号在其它批次已存在。返回明细供前端展示。
     * 带工厂做隔离，弋江与三山的计划互不冲突（去重键也带上工厂）。
     */
    private List<Map<String, Object>> detectConflicts(ParseResult parsed) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ParsedRow pr : parsed.rows) {
            String code = pr.entity.getProductCode();
            String factory = pr.entity.getFactory();
            if (!seen.add(factory + "|" + code)) continue;
            List<WeeklyPlanRowEntity> hits = rowRepository.findConflicts(parsed.year(), parsed.weekNo, code, factory, "");
            for (WeeklyPlanRowEntity h : hits) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productCode", code);
                m.put("existingBatchNo", h.getBatchNo());
                m.put("existingVersion", h.getVersion());
                m.put("existingWeekQty", h.getWeekQty());
                m.put("factory", h.getFactory());
                m.put("project", h.getProject());
                out.add(m);
            }
        }
        return out;
    }

    /**
     * 覆盖确认后：把冲突涉及的旧计划行(同年份+周次+工厂+8D号)连同其班次数量删除，再写入新批次。
     * 带工厂限定，避免覆盖弋江的计划时把三山的同料号计划一起删掉。
     */
    private void supersedeConflicts(ParseResult parsed) {
        Set<String> keys = new LinkedHashSet<>();
        Map<String, String[]> pairs = new LinkedHashMap<>();
        for (ParsedRow pr : parsed.rows) {
            String code = pr.entity.getProductCode();
            String factory = pr.entity.getFactory();
            String key = factory + "|" + code;
            if (keys.add(key)) pairs.put(key, new String[]{code, factory});
        }
        for (String[] p : pairs.values()) {
            for (WeeklyPlanRowEntity old : rowRepository.findConflicts(parsed.year(), parsed.weekNo, p[0], p[1], "")) {
                shiftRepository.findByRowId(old.getId()).forEach(shiftRepository::delete);
                rowRepository.delete(old);
            }
        }
    }

    /** 模板预填：值非空才写入，避免把空串写成"看起来有内容"的单元格。 */
    private void setIfPresent(Row row, int col, String value) {
        if (value == null || value.isBlank()) return;
        Cell c = row.getCell(col);
        if (c == null) c = row.createCell(col);
        c.setCellValue(value.trim());
    }

    private boolean isStopRow(String joined) {
        if (joined == null) return false;
        for (String kw : STOP_KEYWORDS) if (joined.contains(kw)) return true;
        return false;
    }

    private String rowText(Row row) {
        if (row == null) return "";
        StringBuilder sb = new StringBuilder();
        int last = row.getLastCellNum();
        for (int c = 0; c < last; c++) sb.append(rawCellString(row, c)).append(' ');
        return sb.toString().trim();
    }

    /** 取单元格文本：数字单元格直接取数值字符串(避免科学计数)，其余用 DataFormatter。 */
    private String rawCellString(Row row, int col) {
        if (row == null || col < 0) return "";
        Cell c = row.getCell(col);
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c)) {
            double d = c.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return dataFormatter.formatCellValue(c).trim();
    }

    private BigDecimal numOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s.trim().replace(",", "")); } catch (Exception e) { return null; }
    }

    private BigDecimal numZero(String s) {
        BigDecimal v = numOrNull(s);
        return v == null ? BigDecimal.ZERO : v;
    }

    private String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }

    private record DateCol(int col, LocalDate date) {}
    private record ParsedRow(WeeklyPlanRowEntity entity, List<WeeklyPlanShiftQtyEntity> shifts) {}

    private static final class ParseResult {
        String title;
        String excelVersion;
        String factory;
        int weekNo;
        final List<ParsedRow> rows = new ArrayList<>();
        Integer year() { return rows.isEmpty() ? null : rows.get(0).entity.getPlanYear(); }
    }
}
