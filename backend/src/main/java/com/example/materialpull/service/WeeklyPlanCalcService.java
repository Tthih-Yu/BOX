package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.entity.SimpleBomBatchEntity;
import com.example.materialpull.entity.SimpleBomEntity;
import com.example.materialpull.entity.WeeklyPlanRowEntity;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.example.materialpull.repository.SimpleBomRepository;
import com.example.materialpull.repository.WeeklyPlanRowRepository;
import com.example.materialpull.repository.WeeklyPlanShiftQtyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 周计划联动计算引擎：
 *   组件当天需求量 = Σ(用到该组件的各物料当天计划数量) × 料号映射.单根用量
 * 计算结果直接覆盖料号映射的 quantity。
 *
 * 口径：
 *  - 按指定日期汇总各 8D 号白班 + 夜班数量；
 *  - 用当前有效(ACTIVE) BOM 批次把物料展开到组件；
 *  - 组件即料号映射的 lineMaterialCode；多个物料共用同一组件时需求量累加；
 *  - BOM 缺组件、组件缺映射或单根用量未维护时跳过对应项并保留原数量，其余可计算项照常刷新。
 *
 * 手动刷新和每天 0 点自动刷新共用 {@link #calculate(LocalDate, boolean)}，避免两套计算口径。
 */
@Service
@RequiredArgsConstructor
public class WeeklyPlanCalcService {
    private final WeeklyPlanRowRepository rowRepository;
    private final WeeklyPlanShiftQtyRepository shiftRepository;
    private final SimpleBomRepository bomRepository;
    private final SimpleBomService simpleBomService;
    private final MaterialMappingRepository mappingRepository;

    /**
     * 按「某一天」刷新料号映射数量。`quantity` 的语义是**当天用量**(该日白班 + 夜班)，不是整周总量，
     * 所以每天各刷一次、数值随当天计划动态变化。
     *
     * 三条现场口径：
     *  - 单根用量为空或 ≤0 视为「员工还没维护」，跳过该条、不参与计算，避免任何数×0 把数量刷成 0；
     *  - 计划里没出现的组件，保持它原有数量不动（不清零、不归零）；
     *  - BOM 缺组件、组件缺映射只记提示，不阻断其余可算的部分。
     * apply=false 只预览不落库。
     */
    @Transactional
    public CalcResult calculate(LocalDate planDate, boolean apply) {
        if (planDate == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "请指定要计算的日期");
        CalcResult result = new CalcResult();
        result.planDate = planDate;

        // 1) 当天各 8D 号(物料)的数量 = 白班 + 夜班。
        List<Object[]> daily = shiftRepository.sumByProductCodeOnDate(planDate);
        if (daily.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, planDate + " 没有周计划数据，请先上传包含该日期的周计划");

        SimpleBomBatchEntity activeBom = simpleBomService.activeBatch();
        if (activeBom == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "当前没有有效的 BOM 批次，请先在简单BOM页启用一个批次");
        String bomBatch = activeBom.getBatchNo();

        Map<String, BigDecimal> materialPlanQty = new LinkedHashMap<>();
        for (Object[] row : daily) {
            String code = (String) row[0];
            BigDecimal qty = row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue; // 当天不排产，不参与计算
            materialPlanQty.merge(code, qty, BigDecimal::add);
        }
        if (materialPlanQty.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, planDate + " 当天所有数量为 0（未排产），不做任何刷新");

        // 2) 物料 -> 组件(活跃BOM)；多个物料共用同一组件时累加。BOM 缺组件只记提示。
        Map<String, BigDecimal> componentPlanQty = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : materialPlanQty.entrySet()) {
            String material = e.getKey();
            BigDecimal qty = e.getValue();
            List<SimpleBomEntity> components = bomRepository.findByBatchNoAndMaterialCode(bomBatch, material);
            if (components.isEmpty()) {
                result.warnings.add("物料 " + material + " 在当前BOM中找不到组件，已跳过（原数量保持不动）");
                continue;
            }
            for (SimpleBomEntity c : components) {
                componentPlanQty.merge(c.getComponentCode(), qty, BigDecimal::add);
            }
        }

        // 3) 组件 -> 料号映射；当天用量 × 单根用量 = 当天需求量。
        //    单根用量空或 ≤0 = 员工还没维护，跳过不算（否则 ×0 会把数量刷成 0，现场按 0 拉料）。
        List<MappingUpdate> updates = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : componentPlanQty.entrySet()) {
            String component = e.getKey();
            BigDecimal planQty = e.getValue();
            List<MaterialMappingEntity> mappings =
                    mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc(component);
            if (mappings.isEmpty()) {
                result.warnings.add("组件 " + component + " 找不到料号映射，已跳过");
                continue;
            }
            for (MaterialMappingEntity m : mappings) {
                BigDecimal usage = m.getSingleUnitUsage();
                if (usage == null || usage.compareTo(BigDecimal.ZERO) <= 0) {
                    result.skippedNoUsage++;
                    continue;
                }
                updates.add(new MappingUpdate(m, planQty.multiply(usage)));
            }
        }

        result.materialCount = materialPlanQty.size();
        result.componentCount = componentPlanQty.size();
        result.affectedMappings = updates.size();

        for (MappingUpdate u : updates) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("lineMaterialCode", u.mapping.getLineMaterialCode());
            line.put("warehouseCode", u.mapping.getWarehouseCode());
            line.put("mappingOrder", u.mapping.getMappingOrder());
            line.put("singleUnitUsage", u.mapping.getSingleUnitUsage());
            line.put("oldQuantity", u.mapping.getQuantity());
            line.put("newQuantity", u.demand);
            result.preview.add(line);
        }

        if (apply) {
            for (MappingUpdate u : updates) {
                u.mapping.setQuantity(u.demand);
                mappingRepository.save(u.mapping);
            }
            result.applied = true;
        }
        return result;
    }

    private record MappingUpdate(MaterialMappingEntity mapping, BigDecimal demand) {}

    public static class CalcResult {
        /** 计算的是哪一天的用量。 */
        public LocalDate planDate;
        public int materialCount;
        public int componentCount;
        public int affectedMappings;
        /** 因单根用量未维护(空或≤0)而跳过的映射条数，这些条目数量保持原值不动。 */
        public int skippedNoUsage;
        public boolean applied;
        /** BOM 缺组件、组件缺映射等提示，不阻断计算。 */
        public List<String> warnings = new ArrayList<>();
        public List<Map<String, Object>> preview = new ArrayList<>();
    }
}
