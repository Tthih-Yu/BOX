package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.IdGenerator;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.dto.LabelDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.ImportStatus;
import com.example.materialpull.repository.ImportBatchRepository;
import com.example.materialpull.repository.ImportErrorRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ImportService {
    private final ImportBatchRepository batchRepository;
    private final ImportErrorRepository errorRepository;
    private final BasicDataService basicDataService;
    private final LabelService labelService;
    private final DataFormatter dataFormatter = new DataFormatter();

    public ImportBatchEntity importExcel(String type, MultipartFile file, String operator) throws Exception {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件不能为空");
        String importType = validateImportType(type);
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        validateImportFileName(fileName);

        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setBatchNo(IdGenerator.id("IMP"));
        batch.setImportType(importType);
        batch.setFileName(fileName);
        batch.setOperator(operator == null || operator.isBlank() ? OperatorResolver.systemOperator() : operator.trim());
        batchRepository.save(batch);

        try (Workbook wb = openWorkbook(file)) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件没有可读取的工作表");
            int success = 0, fail = 0, total = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row)) continue;
                total++;
                try {
                    importRow(importType, row, batch.getOperator());
                    success++;
                } catch (Exception ex) {
                    fail++;
                    ImportErrorEntity error = new ImportErrorEntity();
                    error.setBatchNo(batch.getBatchNo());
                    error.setRowNo(i + 1);
                    error.setRawData(rowToString(row));
                    error.setErrorMessage(ex.getMessage());
                    errorRepository.save(error);
                }
            }
            batch.setTotalRows(total);
            batch.setSuccessRows(success);
            batch.setFailedRows(fail);
            batch.setStatus(fail == 0 ? ImportStatus.SUCCESS : (success > 0 ? ImportStatus.PARTIAL_SUCCESS : ImportStatus.FAILED));
            batch.setFinishedAt(LocalDateTime.now());
            return batchRepository.save(batch);
        } catch (BusinessException e) {
            markFailed(batch);
            throw e;
        } catch (Exception e) {
            markFailed(batch);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件解析失败，请确认文件是 xlsx/xlsm/xls/csv 格式：" + e.getMessage());
        }
    }

    private void importRow(String type, Row row, String operator) {
        switch (type) {
            case "materials" -> saveMaterial(row);
            case "mappings" -> saveMapping(row);
            case "stationMaterials" -> saveStationMaterial(row);
            case "factoryLabels" -> saveFactoryLabel(row, operator);
            case "siteLabels" -> saveSiteLabel(row, operator);
            case "universalLabels" -> saveUniversalLabel(row, operator);
            default -> throw new IllegalArgumentException("未知导入类型：" + type);
        }
    }

    private void markFailed(ImportBatchEntity batch) {
        if (batch.getTotalRows() == null) batch.setTotalRows(0);
        if (batch.getSuccessRows() == null) batch.setSuccessRows(0);
        if (batch.getFailedRows() == null) batch.setFailedRows(0);
        batch.setStatus(ImportStatus.FAILED);
        batch.setFinishedAt(LocalDateTime.now());
        batchRepository.save(batch);
    }

    private Workbook openWorkbook(MultipartFile file) throws Exception {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) return csvToWorkbook(file.getInputStream());
        return WorkbookFactory.create(file.getInputStream());
    }

    private String validateImportType(String type) {
        String value = type == null ? "" : type.trim();
        Set<String> allowed = Set.of("materials", "mappings", "stationMaterials", "factoryLabels", "siteLabels", "universalLabels");
        if (!allowed.contains(value)) throw new BusinessException(ErrorCode.PARAM_ERROR, "未知导入类型：" + type);
        return value;
    }

    private void validateImportFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT).trim();
        if (lower.isBlank()) return;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || lower.endsWith(".xls") || lower.endsWith(".csv")) return;
        throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 xlsx、xlsm、xls、csv 导入文件");
    }

    private Workbook csvToWorkbook(InputStream in) throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("import");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setTrim(true).setIgnoreEmptyLines(false).build().parse(reader)) {
            int rowIdx = 0;
            for (CSVRecord record : parser) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < record.size(); i++) {
                    String value = record.get(i);
                    if (rowIdx == 1 && i == 0 && value != null && value.startsWith("\uFEFF")) value = value.substring(1);
                    row.createCell(i, CellType.STRING).setCellValue(value == null ? "" : value.trim());
                }
            }
        }
        return wb;
    }

    private void saveMaterial(Row r) {
        MaterialEntity m = new MaterialEntity();
        m.setMaterialCode(cell(r, 0));
        m.setWarehouseMaterialCode(cell(r, 1));
        m.setMaterialName(cell(r, 2));
        m.setSpec(cell(r, 3));
        m.setUnit(cell(r, 4));
        m.setCategory(cell(r, 5));
        basicDataService.saveMaterial(m);
    }

    private void saveMapping(Row r) {
        MaterialMappingEntity m = new MaterialMappingEntity();
        String first = cell(r, 0);
        String second = cell(r, 1);
        String third = cell(r, 2);
        boolean hasSerialColumn = !first.isBlank() && !second.isBlank() && !third.isBlank() && isInteger(first) && !isInteger(second);
        if (hasSerialColumn) {
            // 序号,物料号,仓库代号,盒子大小,数量,工位,用途,备注
            m.setMappingOrder(intVal(r, 0, "排序"));
            m.setLineMaterialCode(second);
            m.setWarehouseCode(third);
            m.setWarehouseMaterialCode(third);
            m.setBoxSize(cell(r, 3));
            m.setQuantity(num(r, 4, "数量"));
            m.setStationCode(cell(r, 5));
            m.setDeliveryType(cell(r, 6).isBlank() ? "NORMAL" : cell(r, 6));
            m.setRemark(cell(r, 7));
        } else {
            // 物料号,仓库代号,盒子大小,数量,工位,排序,用途,备注
            m.setLineMaterialCode(first);
            m.setWarehouseCode(second);
            m.setWarehouseMaterialCode(second);
            m.setBoxSize(third);
            m.setQuantity(num(r, 3, "数量"));
            m.setStationCode(cell(r, 4));
            m.setMappingOrder(intValOrDefault(r, 5, "排序", 1));
            m.setDeliveryType(cell(r, 6).isBlank() ? "NORMAL" : cell(r, 6));
            m.setRemark(cell(r, 7));
        }
        basicDataService.saveMapping(m);
    }

    private void saveStationMaterial(Row r) {
        StationMaterialEntity s = new StationMaterialEntity();
        s.setLineCode(cell(r, 0));
        s.setStationCode(cell(r, 1));
        s.setStationName(cell(r, 2));
        s.setMaterialCode(cell(r, 3));
        s.setMaterialName(cell(r, 4));
        s.setWarehouseMaterialCode(cell(r, 5));
        s.setStandardBoxQty(num(r, 6, "标准盒量"));
        s.setDailyUsage(num(r, 7, "日用量"));
        s.setTriggerQty(num(r, 8, "触发数量"));
        basicDataService.saveStationMaterial(s);
    }

    private void saveFactoryLabel(Row r, String operator) {
        LabelDtos.FactoryPullLabelRequest x = new LabelDtos.FactoryPullLabelRequest();
        x.warehouseCode = cell(r, 0); x.barcodeValue = cell(r, 1); x.primaryScanValue = cell(r, 2);
        x.materialCode = cell(r, 3); x.materialName = cell(r, 4); x.warehouseMaterialCode = cell(r, 5);
        x.warehouseAddress = cell(r, 6); x.sendStationAddress = cell(r, 7); x.boxSize = cell(r, 8);
        x.standardQty = num(r, 9, "数量"); x.unit = cell(r, 10); x.delivererEmployeeNo = cell(r, 11);
        x.lineCode = cell(r, 12); x.stationCode = cell(r, 13); x.stationName = cell(r, 14);
        x.printDate = date(r, 15);
        x.bindBox = "true".equalsIgnoreCase(cell(r, 16)); x.boxSide = cell(r, 17); x.containerType = cell(r, 18);
        x.remark = cell(r, 19); x.operator = operator;
        labelService.createFactoryPullLabel(x);
    }

    private void saveSiteLabel(Row r, String operator) {
        LabelDtos.SiteLabelRequest x = new LabelDtos.SiteLabelRequest();
        x.areaCode = cell(r, 0); x.kanbanCardNo = cell(r, 1); x.barcodeValue = cell(r, 2);
        x.projectCode = cell(r, 3); x.routeName = cell(r, 4); x.deliveryAddress = cell(r, 5);
        x.materialCode = cell(r, 6); x.materialName = cell(r, 7); x.warehouseMaterialCode = cell(r, 8);
        x.standardQty = num(r, 9, "数量"); x.boxSide = cell(r, 10); x.warehouseLocation = cell(r, 11);
        x.specText = cell(r, 12); x.unit = cell(r, 13);
        x.printDate = date(r, 14);
        x.lineCode = cell(r, 15); x.stationCode = cell(r, 16); x.stationName = cell(r, 17);
        x.bindBox = !"false".equalsIgnoreCase(cell(r, 18));
        x.operator = operator;
        labelService.createSiteLabel(x);
    }

    private void saveUniversalLabel(Row r, String operator) {
        LabelDtos.UniversalLabelRequest x = new LabelDtos.UniversalLabelRequest();
        x.labelType = cell(r, 0); x.codeCarrierType = cell(r, 1); x.templateCode = cell(r, 2);
        x.primaryScanValue = cell(r, 3); x.secondaryScanValue = cell(r, 4); x.barcodeValue = cell(r, 5); x.kanbanCardNo = cell(r, 6);
        x.projectCode = cell(r, 7); x.routeName = cell(r, 8); x.deliveryAddress = cell(r, 9);
        x.businessCode = cell(r, 10); x.gridCode = cell(r, 11); x.pointOfUseAddress = cell(r, 12);
        x.routing = cell(r, 13); x.cardNo = intVal(r, 14, "卡片序号"); x.cardTotal = intVal(r, 15, "卡片总数");
        x.supermarketBusiness = cell(r, 16); x.supermarketGrid = cell(r, 17); x.supermarketAddress = cell(r, 18);
        x.materialCode = cell(r, 19); x.materialName = cell(r, 20); x.warehouseMaterialCode = cell(r, 21);
        x.standardQty = num(r, 22, "数量"); x.boxSide = cell(r, 23); x.containerType = cell(r, 24); x.warehouseLocation = cell(r, 25);
        x.specText = cell(r, 26); x.unit = cell(r, 27);
        x.printDate = date(r, 28);
        x.lineCode = cell(r, 29); x.stationCode = cell(r, 30); x.stationName = cell(r, 31);
        x.bindBox = "true".equalsIgnoreCase(cell(r, 32));
        x.rawPayload = cell(r, 33); x.fieldSnapshotJson = cell(r, 34); x.remark = cell(r, 35);
        x.operator = operator;
        labelService.createUniversalLabel(x);
    }

    private String cell(Row r, int i) {
        Cell c = r.getCell(i);
        if (c == null) return "";
        return dataFormatter.formatCellValue(c).trim();
    }

    private LocalDate date(Row r, int i) {
        Cell c = r.getCell(i);
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) return c.getLocalDateTimeCellValue().toLocalDate();
        } catch (Exception ignored) {}
        String s = cell(r, i);
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); }
        catch (Exception e) { throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd，当前值=" + s); }
    }

    private BigDecimal num(Row r, int i, String name) {
        String s = cell(r, i);
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim().replace(",", "")); }
        catch (Exception e) { throw new IllegalArgumentException(name + "必须是数字，当前值=" + s); }
    }

    private Integer intVal(Row r, int i, String name) {
        String s = cell(r, i);
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { throw new IllegalArgumentException(name + "必须是整数，当前值=" + s); }
    }

    private Integer intValOrDefault(Row r, int i, String name, Integer fallback) {
        Integer value = intVal(r, i, name);
        return value == null ? fallback : value;
    }

    private boolean isInteger(String value) {
        if (value == null || value.isBlank()) return false;
        try { Integer.parseInt(value.trim()); return true; }
        catch (Exception e) { return false; }
    }

    private boolean isBlankRow(Row r) {
        int last = Math.max(r.getLastCellNum(), 0);
        for (int i = 0; i < last; i++) if (!cell(r, i).isBlank()) return false;
        return true;
    }

    private String rowToString(Row r) {
        StringBuilder sb = new StringBuilder();
        int last = Math.max(40, r.getLastCellNum());
        for (int i = 0; i < last; i++) sb.append(cell(r, i)).append("|");
        return sb.toString();
    }
}
