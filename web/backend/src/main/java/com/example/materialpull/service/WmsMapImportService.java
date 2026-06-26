package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.IdGenerator;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.entity.ImportBatchEntity;
import com.example.materialpull.entity.ImportErrorEntity;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.ImportStatus;
import com.example.materialpull.repository.ImportBatchRepository;
import com.example.materialpull.repository.ImportErrorRepository;
import com.example.materialpull.repository.MaterialMappingRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WmsMapImportService {
    private final MaterialMappingRepository mappingRepository;
    private final ImportBatchRepository batchRepository;
    private final ImportErrorRepository errorRepository;
    private final DataFormatter formatter = new DataFormatter();

    public ImportBatchEntity importFile(MultipartFile file, String operator) throws Exception {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件不能为空");
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        String lower = fileName.toLowerCase(Locale.ROOT).trim();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xlsm") && !lower.endsWith(".xls")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仓库标签映射仅支持 xlsx、xlsm、xls 文件");
        }
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setBatchNo(IdGenerator.id("IMP"));
        batch.setImportType("warehouseMappings");
        batch.setFileName(fileName);
        batch.setOperator(operator == null || operator.isBlank() ? OperatorResolver.systemOperator() : operator.trim());
        batchRepository.save(batch);
        int total = 0, success = 0, fail = 0;
        Map<String, Integer> seq = new HashMap<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件没有可读取的工作表");
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || blank(row)) continue;
                total++;
                try {
                    saveRow(row, seq);
                    success++;
                } catch (Exception ex) {
                    fail++;
                    ImportErrorEntity error = new ImportErrorEntity();
                    error.setBatchNo(batch.getBatchNo());
                    error.setRowNo(i + 1);
                    error.setRawData(raw(row));
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
            failBatch(batch);
            throw e;
        } catch (Exception e) {
            failBatch(batch);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仓库标签映射导入失败：" + e.getMessage());
        }
    }

    private void saveRow(Row row, Map<String, Integer> seq) {
        String materialCode = cell(row, 1);
        String warehouseCode = cell(row, 2);
        if (materialCode.isBlank()) throw new IllegalArgumentException("物料号不能为空");
        if (warehouseCode.isBlank()) throw new IllegalArgumentException("仓库代号不能为空");
        int orderNo = seq.merge(materialCode, 1, Integer::sum);
        MaterialMappingEntity entity = mappingRepository.findByWarehouseCodeAndEnabledTrue(warehouseCode).orElseGet(MaterialMappingEntity::new);
        entity.setLineMaterialCode(materialCode);
        entity.setWarehouseMaterialCode(materialCode);
        entity.setWarehouseCode(warehouseCode);
        entity.setBoxSize(cell(row, 3));
        entity.setStandardQty(number(row, 4));
        entity.setLabelUsageType(orderNo <= 1 ? "USE" : "SPARE");
        entity.setDeliveryMode(orderNo <= 1 ? "NORMAL" : "URGENT");
        entity.setDescription("warehouse map");
        entity.setRemark("seq=" + cell(row, 0));
        entity.setEnabled(true);
        mappingRepository.save(entity);
    }

    private String cell(Row row, int index) {
        Cell c = row.getCell(index);
        return c == null ? "" : formatter.formatCellValue(c).trim();
    }

    private BigDecimal number(Row row, int index) {
        String value = cell(row, index);
        if (value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.replace(",", "").trim());
    }

    private boolean blank(Row row) {
        for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) if (!cell(row, i).isBlank()) return false;
        return true;
    }

    private String raw(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(row.getLastCellNum(), 5); i++) sb.append(cell(row, i)).append('|');
        return sb.toString();
    }

    private void failBatch(ImportBatchEntity batch) {
        batch.setStatus(ImportStatus.FAILED);
        batch.setFinishedAt(LocalDateTime.now());
        batchRepository.save(batch);
    }
}
