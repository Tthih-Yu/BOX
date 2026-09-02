package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.entity.ProductionPlanEntity;
import com.example.materialpull.entity.MaterialDemandEntity;
import com.example.materialpull.entity.InventoryEntity;
import com.example.materialpull.enums.PlanStatus;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanningServiceDataScopeTest {
    @Mock ProductionPlanRepository planRepository;
    @Mock MaterialDemandRepository demandRepository;
    @Mock PurchaseRequirementRepository purchaseRepository;
    @Mock MaterialBomRepository bomRepository;
    @Mock StationMaterialRepository stationMaterialRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock ReplenishmentTaskRepository taskRepository;
    @Mock MaterialMappingRepository mappingRepository;
    @Mock AuditService auditService;
    @Mock RealtimePushService pushService;
    @Spy DataScopeService dataScopeService = new DataScopeService();
    @InjectMocks PlanningService service;

    @BeforeEach
    void login() {
        RequestContext.setLoginUser(7L, "planner", "计划员", UserRole.PLANNER, "弋江", List.of("T26 Floor"));
    }

    @AfterEach
    void clear() { RequestContext.clear(); }

    @Test
    void planListUsesCurrentFactory() {
        service.plans(null);
        verify(planRepository).findTop1000ByFactoryIgnoreCaseOrderByIdDesc("弋江");
        verify(planRepository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void savePlanOverwritesForgedFactoryWithTrustedFactory() {
        ProductionPlanEntity plan = new ProductionPlanEntity();
        plan.setFactory("三山"); plan.setProductCode("P-1"); plan.setPlanQty(BigDecimal.TEN);
        when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProductionPlanEntity saved = service.savePlan(plan);

        assertEquals("弋江", saved.getFactory());
    }

    @Test
    void releaseRejectsPlanFromAnotherFactoryBeforeGeneratingDemand() {
        ProductionPlanEntity plan = new ProductionPlanEntity();
        plan.setPlanNo("PPC-2"); plan.setFactory("三山"); plan.setStatus(PlanStatus.DRAFT);
        when(planRepository.findByPlanNoForUpdate("PPC-2")).thenReturn(java.util.Optional.of(plan));

        assertThrows(BusinessException.class, () -> service.releaseAndGenerate("PPC-2", false, "planner"));
        verify(demandRepository, never()).save(any());
    }

    @Test
    void inventoryEvaluationAggregatesOnlyDemandFactory() {
        MaterialDemandEntity demand = new MaterialDemandEntity();
        demand.setFactory("弋江"); demand.setWarehouseMaterialCode("WM-1");
        demand.setDemandQty(new BigDecimal("12")); demand.setSingleBoxQty(new BigDecimal("10"));
        InventoryEntity first = inventory("7", "1"), second = inventory("5", "2");
        when(inventoryRepository.findByFactoryIgnoreCaseAndWarehouseMaterialCode("弋江", "WM-1"))
                .thenReturn(List.of(first, second));

        service.evaluateInventory(demand);

        assertEquals(new BigDecimal("12"), demand.getInventoryAvailable());
        assertEquals(new BigDecimal("3"), demand.getSafetyStock());
        verify(inventoryRepository).findByFactoryIgnoreCaseAndWarehouseMaterialCode("弋江", "WM-1");
    }

    @Test
    void inventoryEvaluationRejectsUnscopedDemand() {
        MaterialDemandEntity demand = new MaterialDemandEntity();
        demand.setWarehouseMaterialCode("WM-1");

        assertThrows(BusinessException.class, () -> service.evaluateInventory(demand));

        verifyNoInteractions(inventoryRepository);
    }

    private InventoryEntity inventory(String available, String safety) {
        InventoryEntity entity = new InventoryEntity();
        entity.setAvailableQty(new BigDecimal(available)); entity.setSafetyStock(new BigDecimal(safety));
        return entity;
    }
}
