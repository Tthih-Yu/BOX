package com.example.materialpull.service;

import com.example.materialpull.common.*;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.PlanStatus;
import com.example.materialpull.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IntegrationService {
    private final SapImsLinkRepository linkRepository;
    private final MaterialBomRepository bomRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductionPlanRepository planRepository;
    private final AuditService auditService;

    public List<SapImsLinkEntity> links(String systemCode) {
        if (systemCode == null || systemCode.isBlank()) return linkRepository.findAll(PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        return linkRepository.findBySystemCode(systemCode.trim().toUpperCase(Locale.ROOT));
    }

    @Transactional
    public SapImsLinkEntity upsertLink(FactoryDtos.IntegrationPayload p) {
        if (p == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "接口数据不能为空");
        String system = firstNonBlank(p.systemCode, "SAP").toUpperCase(Locale.ROOT);
        String key = firstNonBlank(p.externalKey, p.productCode, p.materialCode, IdGenerator.id("EXT"));
        SapImsLinkEntity link = linkRepository.findBySystemCodeAndExternalKey(system, key).orElseGet(SapImsLinkEntity::new);
        if (link.getLinkNo() == null) link.setLinkNo(IdGenerator.id("LINK"));
        link.setSystemCode(system); link.setExternalKey(key); link.setProductCode(p.productCode); link.setMaterialCode(p.materialCode); link.setWarehouseMaterialCode(p.warehouseMaterialCode);
        link.setWarehouseCode(p.warehouseCode); link.setProcessCode(p.processCode); link.setLineCode(p.lineCode); link.setStationCode(p.stationCode); link.setRawPayload(p.rawPayload); link.setLastSyncAt(LocalDateTime.now());
        SapImsLinkEntity saved = linkRepository.save(link);
        auditService.iface(system + "_LINK", "IN", p.rawPayload, "linkNo=" + saved.getLinkNo(), true, "SAP/IMS关联关系同步");
        return saved;
    }

    @Transactional
    public MaterialBomEntity receiveSapBom(FactoryDtos.IntegrationPayload p) {
        upsertLink(p);
        MaterialBomEntity b = new MaterialBomEntity();
        b.setBomNo(IdGenerator.id("BOM"));
        b.setBomVersion("SAP_ACTIVE");
        b.setProductCode(p.productCode); b.setProductName(p.productName);
        b.setProcessCode(p.processCode); b.setProcessName(p.processName); b.setBundleCode(p.bundleCode);
        b.setLineCode(p.lineCode); b.setStationCode(p.stationCode); b.setStationName(p.stationName);
        b.setRackCode(p.rackCode); b.setShelfCode(p.shelfCode);
        b.setComponentMaterialCode(p.materialCode); b.setComponentMaterialName(p.materialName); b.setWarehouseMaterialCode(firstNonBlank(p.warehouseMaterialCode, p.warehouseCode, p.materialCode));
        b.setUsageQty(p.usageQty == null || p.usageQty.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : p.usageQty);
        b.setLoadPerBoxQty(p.singleBoxQty == null || p.singleBoxQty.compareTo(BigDecimal.ZERO) <= 0 ? (p.boxQty == null ? BigDecimal.ZERO : p.boxQty) : p.singleBoxQty);
        b.setSafetyStock(p.safetyStock == null ? BigDecimal.ZERO : p.safetyStock);
        b.setMinStock(p.minStock == null ? BigDecimal.ZERO : p.minStock);
        b.setMaxStock(p.maxStock == null ? BigDecimal.ZERO : p.maxStock);
        b.setProductionCycleMinutes(p.productionCycleMinutes == null ? 0 : p.productionCycleMinutes);
        b.setDeliveryCycleMinutes(p.deliveryCycleMinutes == null ? 0 : p.deliveryCycleMinutes);
        b.setSourceSystem("SAP"); b.setRawPayload(p.rawPayload);
        MaterialBomEntity saved = bomRepository.save(b);
        auditService.iface("SAP_BOM", "IN", p.rawPayload, "bomNo=" + saved.getBomNo(), true, "接收SAP BOM/工艺数据：BOM、Bundle、生产周期、架位、送料周期、单盒数量");
        return saved;
    }

    @Transactional
    public InventoryEntity receiveImsInventory(FactoryDtos.IntegrationPayload p) {
        upsertLink(p);
        InventoryEntity inv = inventoryRepository.findFirstByWarehouseMaterialCodeOrderByUpdatedAtDesc(firstNonBlank(p.warehouseMaterialCode, p.warehouseCode, p.materialCode)).orElseGet(InventoryEntity::new);
        inv.setWarehouseCode(firstNonBlank(p.warehouseCode, inv.getWarehouseCode(), "IMS")); inv.setLocationCode(firstNonBlank(inv.getLocationCode(), "IMS-SYNC"));
        inv.setWarehouseMaterialCode(firstNonBlank(p.warehouseMaterialCode, p.warehouseCode, p.materialCode)); inv.setMaterialCode(p.materialCode); inv.setMaterialName(p.materialName);
        inv.setStockQty(p.stockQty == null ? BigDecimal.ZERO : p.stockQty); if (p.safetyStock != null) inv.setSafetyStock(p.safetyStock); inv.setLastCheckedAt(LocalDateTime.now()); inv.setRemark("IMS库存同步；安全库存/额定值用于MPC预测");
        InventoryEntity saved = inventoryRepository.save(inv);
        auditService.iface("IMS_INVENTORY", "IN", p.rawPayload, "warehouseMaterialCode=" + saved.getWarehouseMaterialCode(), true, "接收IMS库存数据");
        return saved;
    }

    @Transactional
    public ProductionPlanEntity receivePpcPlan(FactoryDtos.IntegrationPayload p) {
        ProductionPlanEntity plan = new ProductionPlanEntity();
        plan.setPlanNo(firstNonBlank(p.externalKey, IdGenerator.id("PPC"))); plan.setSourceSystem("PPC"); plan.setProductCode(p.productCode); plan.setProductName(p.productName); plan.setLineCode(p.lineCode); plan.setStationCode(p.stationCode); plan.setBundleCode(p.bundleCode);
        plan.setPlanQty(p.planQty == null || p.planQty.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : p.planQty); plan.setDefaultBoxQty(p.boxQty == null ? new BigDecimal("100") : p.boxQty); plan.setDueAt(p.dueAt); plan.setStatus(PlanStatus.DRAFT); plan.setRemark(p.rawPayload);
        ProductionPlanEntity saved = planRepository.save(plan);
        auditService.iface("PPC_PLAN", "IN", p.rawPayload, "planNo=" + saved.getPlanNo(), true, "接收PPC生产计划");
        return saved;
    }

    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v.trim(); return null; }
}
