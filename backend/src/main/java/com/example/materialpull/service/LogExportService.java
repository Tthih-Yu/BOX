package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 把任意一组 JPA 实体转成 xlsx 字节数组。
 * 用 SXSSF 流式写出，10w 行也撑得住。
 */
@Service
public class LogExportService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DATA_ROWS_PER_SHEET = 1_048_575;

    public byte[] exportToXlsx(String sheetName, List<?> rows, List<String> columns) {
        return exportToXlsx(sheetName, rows, columns, columns);
    }

    public byte[] exportToXlsx(String sheetName, List<?> rows, List<String> columns, List<String> headers) {
        if (columns == null || columns.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "导出列定义不能为空");
        }
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(500)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));
            CellStyle headerStyle = buildHeaderStyle(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers != null && i < headers.size() ? headers.get(i) : columns.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            if (rows != null) {
                for (Object item : rows) {
                    if (item == null) continue;
                    Map<String, Object> bean = beanToMap(item);
                    Row row = sheet.createRow(rowIdx++);
                    for (int c = 0; c < columns.size(); c++) {
                        Cell cell = row.createCell(c);
                        writeCell(cell, bean.get(columns.get(c)));
                    }
                }
            }
            if (rowIdx == 1) {
                Row empty = sheet.createRow(1);
                Cell cell = empty.createCell(0);
                cell.setCellValue("无数据");
                sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, Math.max(columns.size() - 1, 0)));
            }

            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
                workbook.write(out);
                return out.toByteArray();
            } finally {
                workbook.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "导出 Excel 失败：" + e.getMessage());
        }
    }

    /**
     * 分页读取并写入 xlsx，避免为导出而一次性把全部日志装进内存。
     * maxRows 为 0 表示不设上限；大于 0 时最多写入对应行数。
     */
    public <T> void exportPagedToXlsx(OutputStream output, String sheetName, List<String> columns, List<String> headers,
                                      int maxRows, PageFetcher<T> pageFetcher) throws IOException {
        if (columns == null || columns.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "导出列定义不能为空");
        }
        if (pageFetcher == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "导出数据源不能为空");
        }
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(500)) {
            workbook.setCompressTempFiles(true);
            CellStyle headerStyle = buildHeaderStyle(workbook);
            Sheet sheet = createSheetWithHeader(workbook, sheetName, 1, columns, headers, headerStyle);

            int rowIdx = 1;
            int dataRowsOnSheet = 0;
            int written = 0;
            int pageNo = 0;
            int sheetNo = 1;
            final int fetchSize = 500;
            while (maxRows <= 0 || written < maxRows) {
                int requestedSize = maxRows <= 0 ? fetchSize : Math.min(fetchSize, maxRows - written);
                Page<T> page = pageFetcher.fetch(pageNo, requestedSize);
                if (page == null || page.isEmpty()) break;
                for (T item : page.getContent()) {
                    if (item == null) continue;
                    if (dataRowsOnSheet >= MAX_DATA_ROWS_PER_SHEET) {
                        sheet = createSheetWithHeader(workbook, sheetName, ++sheetNo, columns, headers, headerStyle);
                        rowIdx = 1;
                        dataRowsOnSheet = 0;
                    }
                    Map<String, Object> bean = beanToMap(item);
                    Row row = sheet.createRow(rowIdx++);
                    for (int c = 0; c < columns.size(); c++) {
                        writeCell(row.createCell(c), bean.get(columns.get(c)));
                    }
                    dataRowsOnSheet++;
                    written++;
                    if (maxRows > 0 && written >= maxRows) break;
                }
                if (!page.hasNext() || (maxRows > 0 && written >= maxRows)) break;
                pageNo++;
            }
            if (written == 0) {
                Row empty = sheet.createRow(1);
                Cell cell = empty.createCell(0);
                cell.setCellValue("无数据");
                sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, Math.max(columns.size() - 1, 0)));
            }
            workbook.write(output);
            output.flush();
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "导出 Excel 失败：" + e.getMessage());
        }
    }

    @FunctionalInterface
    public interface PageFetcher<T> {
        Page<T> fetch(int page, int size);
    }

    public String buildFileName(String prefix) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return prefix + "-" + stamp + ".xlsx";
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private Sheet createSheetWithHeader(Workbook workbook, String sheetName, int sheetNo, List<String> columns,
                                        List<String> headers, CellStyle headerStyle) {
        String name = sheetNo == 1 ? safeSheetName(sheetName) : pagedSheetName(sheetName, sheetNo);
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers != null && i < headers.size() ? headers.get(i) : columns.get(i));
            cell.setCellStyle(headerStyle);
        }
        return sheet;
    }

    private String pagedSheetName(String sheetName, int sheetNo) {
        String suffix = "-" + sheetNo;
        String base = safeSheetName(sheetName);
        if (base.length() > 31 - suffix.length()) base = base.substring(0, 31 - suffix.length());
        return base + suffix;
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else if (value instanceof LocalDateTime dt) {
            cell.setCellValue(dt.format(FORMATTER));
        } else if (value instanceof LocalDate d) {
            cell.setCellValue(d.toString());
        } else if (value instanceof Date d) {
            cell.setCellValue(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d));
        } else if (value instanceof Enum<?> e) {
            cell.setCellValue(e.name());
        } else {
            String text = String.valueOf(value);
            if (text.length() > 32700) text = text.substring(0, 32700) + "...";
            cell.setCellValue(text);
        }
    }

    private Map<String, Object> beanToMap(Object bean) {
        Map<String, Object> map = new HashMap<>();
        try {
            PropertyDescriptor[] descriptors = Introspector.getBeanInfo(bean.getClass(), Object.class).getPropertyDescriptors();
            for (PropertyDescriptor pd : descriptors) {
                if (pd.getReadMethod() == null) continue;
                map.put(pd.getName(), pd.getReadMethod().invoke(bean));
            }
        } catch (IntrospectionException | ReflectiveOperationException ignored) {
        }
        return map;
    }

    private String safeSheetName(String name) {
        if (name == null || name.isBlank()) return "Sheet1";
        String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        if (cleaned.length() > 31) cleaned = cleaned.substring(0, 31);
        return cleaned;
    }
}
