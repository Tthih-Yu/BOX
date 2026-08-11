package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.IdGenerator;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.entity.ImportErrorEntity;
import com.example.materialpull.entity.SimpleBomBatchEntity;
import com.example.materialpull.entity.SimpleBomEntity;
import com.example.materialpull.enums.SimpleBomBatchStatus;
import com.example.materialpull.repository.ImportErrorRepository;
import com.example.materialpull.repository.SimpleBomBatchRepository;
import com.example.materialpull.repository.SimpleBomJdbcDao;
import com.example.materialpull.repository.SimpleBomRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 简单 BOM(物料8D号 -> 组件8D号)的高速导入与批次切换。
 *
 * 批次切换语义：
 *  - 每次导入生成一个新批次(batchNo)，数据先写入该批次，状态 RUNNING -> READY。
 *  - READY 批次经“激活”后转 ACTIVE，同时把旧的 ACTIVE 批次转 ARCHIVED(保留可回滚)。
 *  - 业务查询与后续计算只认 ACTIVE 批次；导入失败不影响现有 ACTIVE 数据。
 *
 * 性能：CSV 逐行流式读取 + JDBC 批量插入，面向 500 万行量级；不把整份文件读进内存。
 */
@Service
@RequiredArgsConstructor
public class SimpleBomService {
    private final SimpleBomBatchRepository batchRepository;
    private final SimpleBomRepository bomRepository;
    private final SimpleBomJdbcDao bomJdbcDao;
    private final ImportErrorRepository errorRepository;

    /** JDBC 批量插入的攒批行数。 */
    private static final int WRITE_BATCH_SIZE = 2000;
    /** 单批次最多记录的错误行数，避免一份坏文件写爆错误表。 */
    private static final int MAX_ERROR_ROWS = 1000;
    /** 8D 号：恰好 8 位纯数字。 */
    private static final Pattern CODE_8D = Pattern.compile("^\\d{8}$");

    /**
     * 导入一份 BOM 文件为新批次。activateNow=true 时校验通过后立即激活切换为当前有效批次。
     * 导入过程不触碰现有 ACTIVE 批次，失败可安全丢弃该新批次。
     */
    @Transactional
    public SimpleBomBatchEntity importBom(MultipartFile file, boolean activateNow, String operator) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件不能为空");
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        validateFileName(fileName);

        SimpleBomBatchEntity batch = new SimpleBomBatchEntity();
        batch.setBatchNo(IdGenerator.id("BOM"));
        batch.setFileName(fileName);
        batch.setStatus(SimpleBomBatchStatus.RUNNING);
        batch.setOperator(operator == null || operator.isBlank() ? OperatorResolver.systemOperator() : operator.trim());
        batchRepository.save(batch);

        Counter counter = new Counter();
        try {
            streamCsv(file, batch.getBatchNo(), counter);
            batch.setTotalRows(counter.total);
            batch.setSuccessRows(counter.success);
            batch.setFailedRows(counter.fail);
            batch.setFinishedAt(LocalDateTime.now());

            if (counter.success == 0) {
                batch.setStatus(SimpleBomBatchStatus.FAILED);
                batch.setRemark("无有效数据行，导入未生成任何 BOM");
                return batchRepository.save(batch);
            }
            batch.setStatus(SimpleBomBatchStatus.READY);
            batchRepository.save(batch);
            if (activateNow) activate(batch.getBatchNo(), batch.getOperator());
            return batchRepository.findByBatchNo(batch.getBatchNo()).orElse(batch);
        } catch (BusinessException e) {
            markFailed(batch, e.getMessage());
            throw e;
        } catch (Exception e) {
            markFailed(batch, e.getMessage());
            throw new BusinessException(ErrorCode.PARAM_ERROR, "BOM 文件解析失败，请确认是 CSV(UTF-8) 格式：" + e.getMessage());
        }
    }

    /** CSV 逐行流式读取：首行当表头跳过；两列=物料8D、组件8D；攒批高速写库。 */
    private void streamCsv(MultipartFile file, String batchNo, Counter counter) throws Exception {
        List<SimpleBomEntity> buffer = new ArrayList<>(WRITE_BATCH_SIZE);
        List<ImportErrorEntity> errorBuffer = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new BufferedInputStream(file.getInputStream()), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setTrim(true).setIgnoreEmptyLines(true).build().parse(reader)) {
            int rowIdx = 0;
            for (CSVRecord record : parser) {
                if (rowIdx == 0) { rowIdx++; continue; }
                rowIdx++;
                String material = cell(record, 0, rowIdx == 1);
                String component = cell(record, 1, false);
                if (material.isBlank() && component.isBlank()) continue;
                counter.total++;
                try {
                    validateCode(material, "物料8D号");
                    validateCode(component, "组件8D号");
                    SimpleBomEntity e = new SimpleBomEntity();
                    e.setBatchNo(batchNo);
                    e.setMaterialCode(material);
                    e.setComponentCode(component);
                    buffer.add(e);
                    if (buffer.size() >= WRITE_BATCH_SIZE) {
                        bomJdbcDao.batchInsert(buffer);
                        counter.success += buffer.size();
                        buffer.clear();
                    }
                } catch (Exception ex) {
                    counter.fail++;
                    if (errorBuffer.size() < MAX_ERROR_ROWS) {
                        errorBuffer.add(buildError(batchNo, rowIdx, record, ex.getMessage()));
                        if (errorBuffer.size() >= 200) { errorRepository.saveAll(errorBuffer); errorBuffer.clear(); }
                    }
                }
            }
            if (!buffer.isEmpty()) { bomJdbcDao.batchInsert(buffer); counter.success += buffer.size(); buffer.clear(); }
            if (!errorBuffer.isEmpty()) { errorRepository.saveAll(errorBuffer); errorBuffer.clear(); }
        }
    }

    /**
     * 激活切换：把指定 READY/ARCHIVED 批次设为 ACTIVE，原 ACTIVE 批次转 ARCHIVED。
     * 用于导入后手动上线，或从旧批次回滚（回滚即激活一个历史批次）。
     */
    @Transactional
    public SimpleBomBatchEntity activate(String batchNo, String operator) {
        SimpleBomBatchEntity target = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "批次不存在：" + batchNo));
        if (target.getStatus() == SimpleBomBatchStatus.RUNNING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "批次仍在导入中，无法激活");
        }
        if (target.getStatus() == SimpleBomBatchStatus.FAILED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "失败批次无有效数据，无法激活");
        }
        if (target.getStatus() == SimpleBomBatchStatus.ACTIVE) return target;

        for (SimpleBomBatchEntity current : batchRepository.findByStatus(SimpleBomBatchStatus.ACTIVE)) {
            current.setStatus(SimpleBomBatchStatus.ARCHIVED);
            batchRepository.save(current);
        }
        target.setStatus(SimpleBomBatchStatus.ACTIVE);
        target.setActivatedAt(LocalDateTime.now());
        if (operator != null && !operator.isBlank()) target.setOperator(operator.trim());
        return batchRepository.save(target);
    }

    /** 当前有效(ACTIVE)批次；无则返回 null。 */
    public SimpleBomBatchEntity activeBatch() {
        return batchRepository.findFirstByStatus(SimpleBomBatchStatus.ACTIVE).orElse(null);
    }

    public List<SimpleBomBatchEntity> listBatches() {
        return batchRepository.findTop200ByOrderByIdDesc();
    }

    /**
     * 删除批次及其 BOM 明细。不允许删除当前 ACTIVE 批次(先激活别的批次再删)，防止误删线上数据。
     */
    @Transactional
    public void deleteBatch(String batchNo) {
        SimpleBomBatchEntity batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "批次不存在：" + batchNo));
        if (batch.getStatus() == SimpleBomBatchStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前有效批次不能删除，请先激活其它批次");
        }
        bomRepository.deleteByBatchNo(batchNo);
        errorRepository.deleteByBatchNo(batchNo);
        batchRepository.delete(batch);
    }

    /**
     * 单条新增/编辑一行 BOM。归属当前有效(ACTIVE)批次；无有效批次时报错(要求先导入并启用)。
     * 物料8D、组件8D 都做8位纯数字校验。id 为空=新增，非空=更新。
     */
    @Transactional
    public SimpleBomEntity saveRow(Long id, String materialCode, String componentCode) {
        validateCode(materialCode, "物料8D号");
        validateCode(componentCode, "组件8D号");
        SimpleBomBatchEntity active = activeBatch();
        if (active == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "当前没有有效的 BOM 批次，请先导入并启用一个批次再手工维护");
        SimpleBomEntity e;
        if (id == null) {
            e = new SimpleBomEntity();
            e.setBatchNo(active.getBatchNo());
        } else {
            e = bomRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "记录不存在：" + id));
            if (!active.getBatchNo().equals(e.getBatchNo())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "只能编辑当前有效批次的 BOM 行");
            }
        }
        e.setMaterialCode(materialCode.trim());
        e.setComponentCode(componentCode.trim());
        SimpleBomEntity saved = bomRepository.save(e);
        syncBatchCount(active);
        return saved;
    }

    /** 删除单条 BOM 行(仅限当前有效批次)。 */
    @Transactional
    public void deleteRow(Long id) {
        SimpleBomEntity e = bomRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "记录不存在：" + id));
        SimpleBomBatchEntity active = activeBatch();
        if (active == null || !active.getBatchNo().equals(e.getBatchNo())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只能删除当前有效批次的 BOM 行");
        }
        bomRepository.delete(e);
        syncBatchCount(active);
    }

    /** 手工增删后同步批次的成功行数，保证列表统计与实际一致。 */
    private void syncBatchCount(SimpleBomBatchEntity batch) {
        long count = bomRepository.countByBatchNo(batch.getBatchNo());
        batch.setSuccessRows((int) count);
        batch.setTotalRows((int) count);
        batchRepository.save(batch);
    }

    private void markFailed(SimpleBomBatchEntity batch, String reason) {
        batch.setStatus(SimpleBomBatchStatus.FAILED);
        batch.setFinishedAt(LocalDateTime.now());
        batch.setRemark(reason == null ? null : reason.substring(0, Math.min(reason.length(), 480)));
        batchRepository.save(batch);
    }

    private void validateCode(String code, String name) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException(name + "不能为空");
        if (!CODE_8D.matcher(code.trim()).matches()) {
            throw new IllegalArgumentException(name + "必须是8位纯数字，当前值=" + code);
        }
    }

    private void validateFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT).trim();
        if (lower.isBlank() || lower.endsWith(".csv")) return;
        throw new BusinessException(ErrorCode.PARAM_ERROR, "BOM 高速导入仅支持 CSV(UTF-8) 文件");
    }

    private String cell(CSVRecord record, int idx, boolean stripBom) {
        if (idx >= record.size()) return "";
        String v = record.get(idx);
        if (v == null) return "";
        v = v.trim();
        if (!v.isEmpty() && v.charAt(0) == '\uFEFF') v = v.substring(1);
        return v;
    }

    private ImportErrorEntity buildError(String batchNo, int rowNo, CSVRecord record, String message) {
        ImportErrorEntity error = new ImportErrorEntity();
        error.setBatchNo(batchNo);
        error.setRowNo(rowNo);
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < record.size() && i < 8; i++) raw.append(record.get(i)).append("|");
        error.setRawData(raw.toString());
        error.setErrorMessage(message);
        return error;
    }

    /** 导入计数：总行/成功/失败。 */
    private static final class Counter {
        int total = 0, success = 0, fail = 0;
    }
}
