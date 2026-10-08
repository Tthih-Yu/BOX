package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.entity.WarehouseCodeRotationStateEntity;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import com.example.materialpull.repository.WarehouseCodeRotationStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/** 按“工厂 + 总装地址 + 物料号”持久化并轮换仓库代号。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseCodeRotationService {
    private static final int HISTORY_LIMIT = 200;

    private final WarehouseCodeRotationStateRepository stateRepository;
    private final ReplenishmentTaskRepository taskRepository;

    /**
     * 实际建单选择。调用方必须先锁定本物料的映射行，并在同一事务内调用 markTaskSaved。
     */
    public Selection begin(List<MaterialMappingEntity> candidates, String materialCode, String scannedStation) {
        CandidateSet set = candidateSet(candidates, materialCode, scannedStation);
        WarehouseCodeRotationStateEntity state = set.codes().size() > 1
                ? stateRepository.findForUpdate(set.factory(), set.stationKey(), set.materialCode()).orElse(null)
                : null;
        String last = state == null ? findLatestPrintedCode(set) : trimToNull(state.getLastWarehouseCode());
        MaterialMappingEntity selected = selectNext(set.mappings(), last);
        long nextSequence = state == null || state.getSequenceNo() == null ? 1L : state.getSequenceNo() + 1L;
        Selection selection = new Selection(set, state, last, selected, nextSequence);
        registerCompletionLog(selection);
        return selection;
    }

    /** 只读预览下一候选，不锁行、不推进状态；最终结果以正式建单事务为准。 */
    public MaterialMappingEntity peek(List<MaterialMappingEntity> candidates, String materialCode, String scannedStation) {
        CandidateSet set = candidateSet(candidates, materialCode, scannedStation);
        if (set.codes().size() == 1) return set.mappings().get(0);
        WarehouseCodeRotationStateEntity state = stateRepository
                .findByFactoryIgnoreCaseAndStationKeyAndMaterialCode(set.factory(), set.stationKey(), set.materialCode())
                .orElse(null);
        String last = state == null ? findLatestPrintedCode(set) : trimToNull(state.getLastWarehouseCode());
        return selectNext(set.mappings(), last);
    }

    /**
     * 任务已成功写入数据库后推进状态。状态与任务仍处在同一个外层事务中，后续异常会一起回滚。
     */
    public void markTaskSaved(Selection selection, String taskNo) {
        if (selection == null) return;
        selection.taskNo = trimToNull(taskNo);
        selection.taskSaved = true;
        if (selection.candidateCodes().size() <= 1) return;

        WarehouseCodeRotationStateEntity state = selection.state;
        if (state == null) {
            state = new WarehouseCodeRotationStateEntity();
            state.setFactory(selection.factory());
            state.setStationKey(selection.stationKey());
            state.setStationAddress(selection.stationAddress());
            state.setMaterialCode(selection.materialCode());
        }
        state.setStationAddress(selection.stationAddress());
        state.setLastWarehouseCode(selection.selectedWarehouseCode());
        state.setLastTaskNo(selection.taskNo);
        state.setSequenceNo(selection.sequenceNo());
        stateRepository.save(state);
        selection.stateUpdated = true;
    }

    private CandidateSet candidateSet(List<MaterialMappingEntity> candidates, String materialCode, String scannedStation) {
        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "没有可用的料号映射，无法选择仓库代号");
        }
        LinkedHashMap<String, MaterialMappingEntity> distinct = new LinkedHashMap<>();
        for (MaterialMappingEntity mapping : candidates) {
            String code = trimToNull(mapping.getWarehouseCode());
            if (code != null) distinct.putIfAbsent(code.toUpperCase(Locale.ROOT), mapping);
        }
        if (distinct.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_DIRTY,
                    "物料号 " + materialCode + "、工位 " + firstNonBlank(scannedStation, "未提供")
                            + " 没有有效仓库代号，禁止生成空条码标签");
        }

        List<MaterialMappingEntity> mappings = List.copyOf(distinct.values());
        MaterialMappingEntity first = mappings.get(0);
        String factory = required(first.getFactory(), "料号映射缺少工厂，无法维护仓库代号轮换状态");
        String material = required(materialCode, "物料号不能为空");
        String stationAddress = firstNonBlank(first.getDeliveryAddress(), scannedStation, first.getStationCode(), "未维护工位");
        String stationKey = normalizeStation(stationAddress);
        List<String> codes = mappings.stream().map(MaterialMappingEntity::getWarehouseCode).map(String::trim).toList();
        return new CandidateSet(factory, stationKey, stationAddress, material, mappings, codes);
    }

    private MaterialMappingEntity selectNext(List<MaterialMappingEntity> mappings, String lastWarehouseCode) {
        if (mappings.size() == 1) return mappings.get(0);
        int lastIndex = -1;
        for (int i = 0; i < mappings.size(); i++) {
            if (same(mappings.get(i).getWarehouseCode(), lastWarehouseCode)) {
                lastIndex = i;
                break;
            }
        }
        return mappings.get(lastIndex < 0 ? 0 : (lastIndex + 1) % mappings.size());
    }

    /** 新状态首次使用时继承最近一张已打印标签，避免升级部署后立刻重复同一代号。 */
    private String findLatestPrintedCode(CandidateSet set) {
        if (set.codes().size() <= 1) return null;
        List<ReplenishmentTaskEntity> history = taskRepository.findRotationHistory(
                set.factory(), set.materialCode(), set.codes(), PageRequest.of(0, HISTORY_LIMIT));
        for (ReplenishmentTaskEntity task : history) {
            String taskStation = firstNonBlank(task.getDeliveryAddress(), task.getSendStationAddress(), task.getStationCode());
            if (Objects.equals(set.stationKey(), normalizeStation(taskStation))) {
                return trimToNull(task.getWarehouseCode());
            }
        }
        return null;
    }

    private void registerCompletionLog(Selection selection) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                boolean committed = status == TransactionSynchronization.STATUS_COMMITTED;
                boolean success = committed && selection.taskSaved;
                boolean updated = success && selection.stateUpdated;
                log.info("[WarehouseRotate] factory={} station={} material={} candidates={} last={} selected={} sequence={} success={} stateUpdated={} taskNo={}",
                        selection.factory(), selection.stationAddress(), selection.materialCode(),
                        selection.candidateCodes(), firstNonBlank(selection.lastWarehouseCode(), "null"),
                        selection.selectedWarehouseCode(), selection.sequenceNo(), success, updated,
                        firstNonBlank(selection.taskNo, "null"));
            }
        });
    }

    static String normalizeStation(String value) {
        String v = trimToNull(value);
        if (v == null) return "__UNSPECIFIED__";
        return v.replace('～', '~')
                .replace('－', '-')
                .replace('　', ' ')
                .replace('（', '(')
                .replace('）', ')')
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }

    private static boolean same(String left, String right) {
        String l = trimToNull(left);
        String r = trimToNull(right);
        return l != null && r != null && l.equalsIgnoreCase(r);
    }

    private static String required(String value, String message) {
        String result = trimToNull(value);
        if (result == null) throw new BusinessException(ErrorCode.DATA_DIRTY, message);
        return result;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            String result = trimToNull(value);
            if (result != null) return result;
        }
        return null;
    }

    private record CandidateSet(String factory, String stationKey, String stationAddress, String materialCode,
                                List<MaterialMappingEntity> mappings, List<String> codes) {}

    public static final class Selection {
        private final CandidateSet set;
        private final WarehouseCodeRotationStateEntity state;
        private final String lastWarehouseCode;
        private final MaterialMappingEntity selectedMapping;
        private final long sequenceNo;
        private boolean taskSaved;
        private boolean stateUpdated;
        private String taskNo;

        private Selection(CandidateSet set, WarehouseCodeRotationStateEntity state, String lastWarehouseCode,
                          MaterialMappingEntity selectedMapping, long sequenceNo) {
            this.set = set;
            this.state = state;
            this.lastWarehouseCode = lastWarehouseCode;
            this.selectedMapping = selectedMapping;
            this.sequenceNo = sequenceNo;
        }

        public MaterialMappingEntity selectedMapping() { return selectedMapping; }
        public String factory() { return set.factory(); }
        public String stationKey() { return set.stationKey(); }
        public String stationAddress() { return set.stationAddress(); }
        public String materialCode() { return set.materialCode(); }
        public List<String> candidateCodes() { return set.codes(); }
        public String lastWarehouseCode() { return lastWarehouseCode; }
        public String selectedWarehouseCode() { return selectedMapping.getWarehouseCode(); }
        public long sequenceNo() { return sequenceNo; }
    }
}
