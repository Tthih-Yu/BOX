package com.example.materialpull.service;

import com.example.materialpull.common.*;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanningService {
    private final ProductionPlanRepository planRepository;
    private final MaterialDemandRepository demandRepository;
    private final PurchaseRequirementRepository purchaseRepository;
    private final MaterialBomRepository bomRepository;
    private final StationMaterialRepository stationMaterialRepository;
    private final InventoryRepository inventoryRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final AuditService auditService;
    private final RealtimePushService pushService;

    public List<ProductionPlanEntity> plans(String status) {
        if (status == null || status.isBlank()) return planRepository.findAll(topPage()).getContent();
        return planRepository.findByStatus(PlanStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
    }

    public List<MaterialBomEntity> boms() { return bomRepository.findAll(topPage()).getContent(); }

    public List<StationMaterialEntity> processConfigs() { return stationMaterialRepository.findAll(topPage()).getContent(); }

    public List<MaterialDemandEntity> demands(String status) {
        if (status == null || status.isBlank()) return demandRepository.findAll(topPage()).getContent();
        return demandRepository.findByStatus(DemandStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
    }

    public List<PurchaseRequirementEntity> purchases(String status) {
        if (status == null || status.isBlank()) return purchaseRepository.findAll(topPage()).getContent();
        return purchaseRepository.findByStatus(PurchaseStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
    }

    public List<FactoryDtos.InventoryAdjustmentRow> inventoryAdjustments() {
        return demandRepository.findAll(topPage()).getContent().stream().map(this::toAdjustmentRow).toList();
    }

    public List<FactoryDtos.MaterialForecastRow> materialForecasts() {
        Map<String, PurchaseRequirementEntity> purchaseByDemand = purchaseRepository.findAll(topPage()).getContent().stream()
                .collect(Collectors.toMap(PurchaseRequirementEntity::getDemandNo, x -> x, (a, b) -> a));
        return demandRepository.findAll(topPage()).getContent().stream().map(d -> toForecastRow(d, purchaseByDemand.get(d.getDemandNo()))).toList();
    }

    private PageRequest topPage() { return PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "id")); }

    @Transactional
    public MaterialBomEntity saveBom(MaterialBomEntity e) {
        if (e == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "BOM不能为空");
        if (e.getBomNo() == null || e.getBomNo().isBlank()) e.setBomNo(IdGenerator.id("BOM"));
        if (e.getBomVersion() == null || e.getBomVersion().isBlank()) e.setBomVersion("ACTIVE");
        if (e.getProductCode() == null || e.getProductCode().isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "产品编码不能为空");
        if (e.getComponentMaterialCode() == null || e.getComponentMaterialCode().isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "组件料号不能为空");
        if (e.getWarehouseMaterialCode() == null || e.getWarehouseMaterialCode().isBlank()) e.setWarehouseMaterialCode(e.getComponentMaterialCode());
        if (e.getUsageQty() == null || e.getUsageQty().compareTo(BigDecimal.ZERO) <= 0) e.setUsageQty(BigDecimal.ONE);
        if (e.getLoadPerBoxQty() == null || e.getLoadPerBoxQty().compareTo(BigDecimal.ZERO) <= 0) e.setLoadPerBoxQty(BigDecimal.ZERO);
        MaterialBomEntity saved = bomRepository.save(e);
        auditService.iface("SAP_BOM", "IN", e.getRawPayload(), "BOM=" + saved.getBomNo(), true, "SAP-BOM与IMS拉动关系入库，包含Bundle、生产周期、架位和单盒数量");
        return saved;
    }

    @Transactional
    public ProductionPlanEntity savePlan(ProductionPlanEntity p) {
        if (p == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "生产计划不能为空");
        if (p.getPlanNo() == null || p.getPlanNo().isBlank()) p.setPlanNo(IdGenerator.id("PPC"));
        if (p.getPlanQty() == null || p.getPlanQty().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR, "计划数量必须大于0");
        if (p.getStatus() == null) p.setStatus(PlanStatus.DRAFT);
        p.setOperator(OperatorResolver.currentOperator());
        ProductionPlanEntity saved = planRepository.save(p);
        auditService.iface("PPC_PLAN", "IN", p.getRemark(), "PLAN=" + saved.getPlanNo(), true, "PPC生产计划入库");
        return saved;
    }

    @Transactional
    public StationMaterialEntity saveProcessConfig(StationMaterialEntity e) {
        if (e == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "工艺/仓库录入信息不能为空");
        if (e.getLineCode() == null || e.getLineCode().isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "产线不能为空");
        if (e.getStationCode() == null || e.getStationCode().isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "工位不能为空");
        if (e.getMaterialCode() == null || e.getMaterialCode().isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "物料编码不能为空");
        if (e.getMaterialName() == null || e.getMaterialName().isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "物料名称不能为空");
        if (e.getWarehouseMaterialCode() == null || e.getWarehouseMaterialCode().isBlank()) e.setWarehouseMaterialCode(e.getMaterialCode());
        if (safe(e.getSingleBoxQty()).compareTo(BigDecimal.ZERO) <= 0) e.setSingleBoxQty(firstPositive(e.getStandardBoxQty(), BigDecimal.ONE));
        if (safe(e.getStandardBoxQty()).compareTo(BigDecimal.ZERO) <= 0) e.setStandardBoxQty(e.getSingleBoxQty());
        StationMaterialEntity saved = stationMaterialRepository.save(e);
        auditService.iface("PROCESS_MASTER", "IN", saved.getRemark(), "station=" + saved.getStationCode() + ",material=" + saved.getMaterialCode(), true, "工艺/仓库录入信息保存，包含Bundle、生产周期、架位、送料周期、单盒数量、安全库存、MPC阈值");
        return saved;
    }

    @Transactional
    public FactoryDtos.PlanGenerateResult releaseAndGenerate(String planNo, boolean createPullTasks, String operator) {
        ProductionPlanEntity plan = planRepository.findByPlanNoForUpdate(planNo).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "计划不存在：" + planNo));
        if (plan.getStatus() == PlanStatus.CLOSED || plan.getStatus() == PlanStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "计划已关闭或取消，禁止生成需求：" + plan.getStatus());
        }
        if (plan.getStatus() == PlanStatus.DEMAND_GENERATED || demandRepository.existsByPlanNo(planNo) || taskRepository.existsByPlanNo(planNo)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "该计划已经生成过物料需求或拉动任务，请勿重复生成：" + planNo);
        }
        plan.setStatus(PlanStatus.RELEASED);
        plan.setReleasedAt(LocalDateTime.now());
        plan.setOperator(firstNonBlank(operator, plan.getOperator(), OperatorResolver.currentOperator(), OperatorResolver.systemOperator()));

        List<MaterialBomEntity> boms = bomRepository.findByProductCodeAndEnabledTrue(plan.getProductCode());
        List<StationMaterialEntity> stationMaterials = loadStationMaterials(plan);

        List<MaterialDemandEntity> demands = new ArrayList<>();
        if (!boms.isEmpty()) {
            for (MaterialBomEntity b : boms) demands.add(createDemandFromBom(plan, b, stationMaterials));
        } else {
            for (StationMaterialEntity sm : stationMaterials) demands.add(createDemandFromStation(plan, sm));
        }
        if (demands.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "未找到BOM或工位用料，无法生成物料需求");

        List<ReplenishmentTaskEntity> tasks = new ArrayList<>();
        List<PurchaseRequirementEntity> purchases = new ArrayList<>();
        for (MaterialDemandEntity d : demands) {
            evaluateInventory(d);
            demandRepository.save(d);
            if (safe(d.getShortageQty()).compareTo(BigDecimal.ZERO) > 0) {
                purchases.add(createPurchase(plan, d));
                d.setStatus(DemandStatus.PURCHASE_REQUIRED);
            }
            if (createPullTasks && safe(d.getInventoryAvailable()).compareTo(BigDecimal.ZERO) > 0) {
                List<ReplenishmentTaskEntity> createdTasks = createPlanPullTasks(plan, d);
                for (ReplenishmentTaskEntity t : createdTasks) taskRepository.save(t);
                if (!createdTasks.isEmpty()) {
                    d.setTaskNo(createdTasks.get(0).getTaskNo());
                    d.setTaskNos(createdTasks.stream().map(ReplenishmentTaskEntity::getTaskNo).collect(Collectors.joining(",")));
                    if (d.getStatus() != DemandStatus.PURCHASE_REQUIRED) d.setStatus(DemandStatus.PULL_TASK_CREATED);
                    tasks.addAll(createdTasks);
                }
            }
            demandRepository.save(d);
        }
        plan.setStatus(PlanStatus.DEMAND_GENERATED);
        planRepository.save(plan);
        auditService.iface("PPC_MRP_MPC", "INTERNAL", "planNo=" + planNo, "demands=" + demands.size() + ",tasks=" + tasks.size() + ",purchases=" + purchases.size(), true, "PPC计划生成单盒物料需求、库存调整对照表和MPC采购建议");
        pushService.publish("plans", plan);
        FactoryDtos.PlanGenerateResult r = new FactoryDtos.PlanGenerateResult();
        r.plan = plan; r.demands = demands; r.tasks = tasks; r.purchases = purchases;
        r.adjustments = demands.stream().map(this::toAdjustmentRow).toList();
        r.forecasts = demands.stream().map(d -> toForecastRow(d, purchases.stream().filter(p -> Objects.equals(p.getDemandNo(), d.getDemandNo())).findFirst().orElse(null))).toList();
        return r;
    }

    @Transactional
    public PurchaseRequirementEntity submitPurchase(String purchaseNo, String operator) {
        PurchaseRequirementEntity p = purchaseRepository.findByPurchaseNo(purchaseNo).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购需求不存在：" + purchaseNo));
        p.setStatus(PurchaseStatus.SUBMITTED);
        p.setSubmittedAt(LocalDateTime.now());
        p.setMpcRemark(firstNonBlank(p.getMpcRemark(), "MPC预测采购已提交，操作人=" + firstNonBlank(operator, OperatorResolver.systemOperator())));
        purchaseRepository.save(p);
        auditService.iface("MPC_PURCHASE_REQUIREMENT", "OUT", "purchaseNo=" + purchaseNo, "SUBMITTED", true, "采购需求提交MPC");
        pushService.publish("purchases", p);
        return p;
    }

    private List<StationMaterialEntity> loadStationMaterials(ProductionPlanEntity plan) {
        List<StationMaterialEntity> stationMaterials;
        if (plan.getStationCode() != null && !plan.getStationCode().isBlank()) {
            stationMaterials = stationMaterialRepository.findByLineCodeAndStationCodeAndEnabledTrue(plan.getLineCode(), plan.getStationCode());
        } else {
            stationMaterials = stationMaterialRepository.findByLineCodeAndEnabledTrue(plan.getLineCode());
        }
        stationMaterials = stationMaterials.stream()
                .filter(x -> Boolean.TRUE.equals(x.getForecastEnabled()))
                .toList();
        if (plan.getBundleCode() != null && !plan.getBundleCode().isBlank()) {
            String bundle = plan.getBundleCode().trim();
            stationMaterials = stationMaterials.stream().filter(x -> x.getBundleCode() == null || x.getBundleCode().isBlank() || bundle.equalsIgnoreCase(x.getBundleCode())).toList();
        }
        return stationMaterials;
    }

    private MaterialDemandEntity createDemandFromBom(ProductionPlanEntity plan, MaterialBomEntity b, List<StationMaterialEntity> processConfigs) {
        StationMaterialEntity sm = matchProcessConfig(b, processConfigs);
        MaterialDemandEntity d = new MaterialDemandEntity();
        d.setDemandNo(IdGenerator.id("DMD"));
        d.setPlanNo(plan.getPlanNo());
        d.setLineCode(firstNonBlank(b.getLineCode(), sm == null ? null : sm.getLineCode(), plan.getLineCode()));
        d.setStationCode(firstNonBlank(b.getStationCode(), sm == null ? null : sm.getStationCode(), plan.getStationCode()));
        d.setStationName(firstNonBlank(b.getStationName(), sm == null ? null : sm.getStationName()));
        d.setProductCode(plan.getProductCode());
        d.setBundleCode(firstNonBlank(plan.getBundleCode(), b.getBundleCode(), sm == null ? null : sm.getBundleCode()));
        d.setProcessCode(firstNonBlank(b.getProcessCode(), sm == null ? null : sm.getProcessCode()));
        d.setRackCode(firstNonBlank(b.getRackCode(), sm == null ? null : sm.getRackCode()));
        d.setShelfCode(firstNonBlank(b.getShelfCode(), sm == null ? null : sm.getShelfCode()));
        d.setMaterialCode(b.getComponentMaterialCode());
        d.setMaterialName(b.getComponentMaterialName());
        d.setWarehouseMaterialCode(firstNonBlank(b.getWarehouseMaterialCode(), sm == null ? null : sm.getWarehouseMaterialCode(), b.getComponentMaterialCode()));
        d.setUsageQty(safe(b.getUsageQty()));
        d.setPlanQty(safe(plan.getPlanQty()));
        d.setDemandQty(safe(plan.getPlanQty()).multiply(safe(b.getUsageQty())).setScale(0, RoundingMode.CEILING));
        d.setBoxQty(firstPositive(b.getLoadPerBoxQty(), sm == null ? null : sm.getSingleBoxQty(), sm == null ? null : sm.getStandardBoxQty(), plan.getDefaultBoxQty(), new BigDecimal("100")));
        d.setSingleBoxQty(d.getBoxQty());
        d.setSafetyStock(firstPositive(b.getSafetyStock(), sm == null ? null : sm.getSafetyStock(), BigDecimal.ZERO));
        d.setMpcThresholdQty(firstPositive(sm == null ? null : sm.getMpcThresholdQty(), b.getMinStock(), d.getSafetyStock()));
        d.setProductionCycleMinutes(firstPositiveInt(b.getProductionCycleMinutes(), sm == null ? null : sm.getProductionCycleMinutes()));
        d.setDeliveryCycleMinutes(firstPositiveInt(b.getDeliveryCycleMinutes(), sm == null ? null : sm.getDeliveryCycleMinutes()));
        d.setRemark("SAP-BOM展开，按工艺/仓库录入信息补齐单盒数量、架位、送料周期");
        return d;
    }

    private MaterialDemandEntity createDemandFromStation(ProductionPlanEntity plan, StationMaterialEntity sm) {
        MaterialDemandEntity d = new MaterialDemandEntity();
        d.setDemandNo(IdGenerator.id("DMD"));
        d.setPlanNo(plan.getPlanNo());
        d.setLineCode(sm.getLineCode());
        d.setStationCode(sm.getStationCode());
        d.setStationName(sm.getStationName());
        d.setProductCode(plan.getProductCode());
        d.setBundleCode(firstNonBlank(plan.getBundleCode(), sm.getBundleCode()));
        d.setProcessCode(sm.getProcessCode());
        d.setRackCode(sm.getRackCode());
        d.setShelfCode(sm.getShelfCode());
        d.setMaterialCode(sm.getMaterialCode());
        d.setMaterialName(sm.getMaterialName());
        d.setWarehouseMaterialCode(sm.getWarehouseMaterialCode());
        BigDecimal usage = safe(sm.getDailyUsage()).compareTo(BigDecimal.ZERO) > 0 ? safe(sm.getDailyUsage()).divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP) : BigDecimal.ONE;
        d.setUsageQty(usage);
        d.setPlanQty(safe(plan.getPlanQty()));
        d.setDemandQty(safe(plan.getPlanQty()).multiply(usage).setScale(0, RoundingMode.CEILING));
        d.setBoxQty(firstPositive(sm.getSingleBoxQty(), sm.getStandardBoxQty(), plan.getDefaultBoxQty(), new BigDecimal("100")));
        d.setSingleBoxQty(d.getBoxQty());
        d.setSafetyStock(safe(sm.getSafetyStock()));
        d.setMpcThresholdQty(firstPositive(sm.getMpcThresholdQty(), sm.getSafetyStock(), sm.getMinStock()));
        d.setProductionCycleMinutes(firstPositiveInt(sm.getProductionCycleMinutes()));
        d.setDeliveryCycleMinutes(firstPositiveInt(sm.getDeliveryCycleMinutes()));
        d.setRemark("工位用料生成，按单盒数量输出拉动任务");
        return d;
    }

    private StationMaterialEntity matchProcessConfig(MaterialBomEntity b, List<StationMaterialEntity> configs) {
        if (configs == null || configs.isEmpty()) return null;
        return configs.stream().filter(x -> same(x.getWarehouseMaterialCode(), b.getWarehouseMaterialCode()) || same(x.getMaterialCode(), b.getComponentMaterialCode()))
                .filter(x -> b.getStationCode() == null || b.getStationCode().isBlank() || same(x.getStationCode(), b.getStationCode()))
                .findFirst().orElse(null);
    }

    private void evaluateInventory(MaterialDemandEntity d) {
        InventoryEntity inv = inventoryRepository.findFirstByWarehouseMaterialCodeOrderByUpdatedAtDesc(d.getWarehouseMaterialCode()).orElse(null);
        BigDecimal available = inv == null ? BigDecimal.ZERO : safe(inv.getAvailableQty());
        BigDecimal safety = firstPositive(d.getSafetyStock(), inv == null ? null : inv.getSafetyStock(), BigDecimal.ZERO);
        BigDecimal threshold = firstPositive(d.getMpcThresholdQty(), safety);
        d.setInventoryAvailable(available);
        d.setSafetyStock(safety);
        d.setMpcThresholdQty(threshold);
        BigDecimal requiredIncludingSafety = safe(d.getDemandQty()).add(safety);
        d.setShortageQty(requiredIncludingSafety.subtract(available).max(BigDecimal.ZERO));
        d.setInventoryDifferenceQty(available.subtract(requiredIncludingSafety));
        d.setAdjustmentQty(requiredIncludingSafety.subtract(available).max(BigDecimal.ZERO));
        BigDecimal boxQty = firstPositive(d.getSingleBoxQty(), d.getBoxQty(), new BigDecimal("100"));
        d.setSingleBoxQty(boxQty);
        d.setBoxQty(boxQty);
        d.setTaskBoxCount(safe(d.getDemandQty()).divide(boxQty, 0, RoundingMode.CEILING).intValue());
    }

    private PurchaseRequirementEntity createPurchase(ProductionPlanEntity plan, MaterialDemandEntity d) {
        PurchaseRequirementEntity p = new PurchaseRequirementEntity();
        p.setPurchaseNo(IdGenerator.id("MPC"));
        p.setDemandNo(d.getDemandNo());
        p.setPlanNo(plan.getPlanNo());
        p.setMaterialCode(d.getMaterialCode());
        p.setMaterialName(d.getMaterialName());
        p.setWarehouseMaterialCode(d.getWarehouseMaterialCode());
        p.setCurrentStock(d.getInventoryAvailable());
        p.setSafetyStock(d.getSafetyStock());
        p.setForecastQty(d.getDemandQty());
        p.setThresholdQty(firstPositive(d.getMpcThresholdQty(), d.getSafetyStock()));
        p.setShortageQty(d.getShortageQty());
        p.setRequestQty(d.getShortageQty());
        p.setForecastSource("PPC计划+库存扣减+MPC额定阈值");
        p.setMpcRemark("由PPC计划 " + plan.getPlanNo() + "、库存差异和MPC阈值自动生成");
        return purchaseRepository.save(p);
    }

    private List<ReplenishmentTaskEntity> createPlanPullTasks(ProductionPlanEntity plan, MaterialDemandEntity d) {
        BigDecimal availableForPlan = safe(d.getDemandQty()).min(safe(d.getInventoryAvailable()));
        if (availableForPlan.compareTo(BigDecimal.ZERO) <= 0) return List.of();
        BigDecimal boxQty = firstPositive(d.getSingleBoxQty(), d.getBoxQty(), new BigDecimal("100"));
        int total = availableForPlan.divide(boxQty, 0, RoundingMode.CEILING).intValue();
        List<ReplenishmentTaskEntity> tasks = new ArrayList<>();
        BigDecimal remaining = availableForPlan;
        for (int i = 1; i <= total; i++) {
            BigDecimal taskQty = remaining.min(boxQty);
            remaining = remaining.subtract(taskQty);
            ReplenishmentTaskEntity t = new ReplenishmentTaskEntity();
            t.setTaskNo(IdGenerator.id("RP"));
            t.setPlanNo(plan.getPlanNo());
            t.setDemandNo(d.getDemandNo());
            t.setLineCode(d.getLineCode());
            t.setStationCode(d.getStationCode());
            t.setStationName(d.getStationName());
            t.setMaterialCode(d.getMaterialCode());
            t.setMaterialName(d.getMaterialName());
            t.setWarehouseMaterialCode(d.getWarehouseMaterialCode());
            t.setWarehouseLocation(joinLocation(d.getRackCode(), d.getShelfCode()));
            t.setRequestQty(taskQty);
            t.setStatus(TaskStatus.CREATED);
            t.setPriority(safe(d.getShortageQty()).compareTo(BigDecimal.ZERO) > 0 ? PriorityLevel.HIGH : PriorityLevel.NORMAL);
            t.setCreatedBy(firstNonBlank(plan.getOperator(), OperatorResolver.systemOperator()));
            t.setDeadlineAt(calculateDue(plan.getDueAt(), d.getDeliveryCycleMinutes()));
            t.setLastActionAt(LocalDateTime.now());
            t.setLabelUsageType("PLAN_SINGLE_BOX");
            t.setDeliveryMode("NORMAL");
            t.setSingleBoxTask(true);
            t.setBoxSeq(i);
            t.setBoxTotal(total);
            t.setRemark("PPC计划自动生成单盒拉动任务，一单一盒；planNo=" + plan.getPlanNo() + "，demandNo=" + d.getDemandNo());
            tasks.add(t);
        }
        return tasks;
    }

    private FactoryDtos.InventoryAdjustmentRow toAdjustmentRow(MaterialDemandEntity d) {
        FactoryDtos.InventoryAdjustmentRow r = new FactoryDtos.InventoryAdjustmentRow();
        r.planNo = d.getPlanNo();
        r.demandNo = d.getDemandNo();
        r.lineCode = d.getLineCode();
        r.stationCode = d.getStationCode();
        r.stationName = d.getStationName();
        r.bundleCode = d.getBundleCode();
        r.materialCode = d.getMaterialCode();
        r.materialName = d.getMaterialName();
        r.warehouseMaterialCode = d.getWarehouseMaterialCode();
        r.demandQty = safe(d.getDemandQty());
        r.singleBoxQty = firstPositive(d.getSingleBoxQty(), d.getBoxQty(), BigDecimal.ZERO);
        r.taskBoxCount = d.getTaskBoxCount() == null ? 0 : d.getTaskBoxCount();
        r.currentStock = safe(d.getInventoryAvailable());
        r.safetyStock = safe(d.getSafetyStock());
        r.inventoryDifferenceQty = safe(d.getInventoryDifferenceQty());
        r.adjustmentQty = safe(d.getAdjustmentQty());
        r.printTarget = "生产打印机/仓库打印机";
        r.status = d.getStatus() == null ? null : d.getStatus().name();
        return r;
    }

    private FactoryDtos.MaterialForecastRow toForecastRow(MaterialDemandEntity d, PurchaseRequirementEntity purchase) {
        FactoryDtos.MaterialForecastRow r = new FactoryDtos.MaterialForecastRow();
        r.planNo = d.getPlanNo();
        r.demandNo = d.getDemandNo();
        r.materialCode = d.getMaterialCode();
        r.materialName = d.getMaterialName();
        r.warehouseMaterialCode = d.getWarehouseMaterialCode();
        r.forecastQty = safe(purchase == null ? d.getDemandQty() : purchase.getForecastQty());
        r.currentStock = safe(d.getInventoryAvailable());
        r.thresholdQty = firstPositive(d.getMpcThresholdQty(), d.getSafetyStock());
        r.purchaseSuggestionQty = safe(purchase == null ? d.getShortageQty() : purchase.getRequestQty());
        r.source = purchase == null ? "PPC计划+库存扣减" : purchase.getForecastSource();
        r.status = purchase == null ? d.getStatus().name() : purchase.getStatus().name();
        return r;
    }

    private LocalDateTime calculateDue(LocalDateTime dueAt, Integer deliveryCycleMinutes) {
        if (dueAt != null) return dueAt;
        int minutes = deliveryCycleMinutes == null || deliveryCycleMinutes <= 0 ? 480 : deliveryCycleMinutes;
        return LocalDateTime.now().plusMinutes(minutes);
    }

    private String joinLocation(String rackCode, String shelfCode) {
        String r = firstNonBlank(rackCode);
        String s = firstNonBlank(shelfCode);
        if (r == null) return s;
        if (s == null) return r;
        return r + "/" + s;
    }

    private boolean same(String a, String b) { return a != null && b != null && a.trim().equalsIgnoreCase(b.trim()); }
    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private BigDecimal firstPositive(BigDecimal... values) { for (BigDecimal v : values) if (v != null && v.compareTo(BigDecimal.ZERO) > 0) return v; return BigDecimal.ZERO; }
    private Integer firstPositiveInt(Integer... values) { for (Integer v : values) if (v != null && v > 0) return v; return 0; }
    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v.trim(); return null; }
}
