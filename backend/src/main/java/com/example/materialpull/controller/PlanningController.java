package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.PlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/planning")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
public class PlanningController {
    private final PlanningService service;

    @GetMapping("/plans") public ApiResponse<List<ProductionPlanEntity>> plans(@RequestParam(required = false) String status) { return ApiResponse.ok(service.plans(status)); }
    @PostMapping("/plans") @RequireRoles({UserRole.PLANNER}) public ApiResponse<ProductionPlanEntity> savePlan(@RequestBody ProductionPlanEntity p) { return ApiResponse.ok(service.savePlan(p)); }
    @PostMapping("/plans/{planNo}/generate") @RequireRoles({UserRole.PLANNER}) public ApiResponse<FactoryDtos.PlanGenerateResult> generate(@PathVariable String planNo, @RequestParam(defaultValue = "true") boolean createPullTasks) { return ApiResponse.ok(service.releaseAndGenerate(planNo, createPullTasks, OperatorResolver.currentOperator())); }

    @GetMapping("/boms") public ApiResponse<List<MaterialBomEntity>> boms() { return ApiResponse.ok(service.boms()); }
    @PostMapping("/boms") @RequireRoles({UserRole.PLANNER}) public ApiResponse<MaterialBomEntity> saveBom(@RequestBody MaterialBomEntity e) { return ApiResponse.ok(service.saveBom(e)); }

    @GetMapping("/process-configs") public ApiResponse<List<StationMaterialEntity>> processConfigs() { return ApiResponse.ok(service.processConfigs()); }
    @PostMapping("/process-configs") @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE}) public ApiResponse<StationMaterialEntity> saveProcessConfig(@RequestBody StationMaterialEntity e) { return ApiResponse.ok(service.saveProcessConfig(e)); }

    @GetMapping("/demands") public ApiResponse<List<MaterialDemandEntity>> demands(@RequestParam(required = false) String status) { return ApiResponse.ok(service.demands(status)); }
    @GetMapping("/inventory-adjustments") public ApiResponse<List<FactoryDtos.InventoryAdjustmentRow>> inventoryAdjustments() { return ApiResponse.ok(service.inventoryAdjustments()); }
    @GetMapping("/material-forecasts") public ApiResponse<List<FactoryDtos.MaterialForecastRow>> materialForecasts() { return ApiResponse.ok(service.materialForecasts()); }

    @GetMapping("/purchases") public ApiResponse<List<PurchaseRequirementEntity>> purchases(@RequestParam(required = false) String status) { return ApiResponse.ok(service.purchases(status)); }
    @PostMapping("/purchases/{purchaseNo}/submit") @RequireRoles({UserRole.PLANNER}) public ApiResponse<PurchaseRequirementEntity> submitPurchase(@PathVariable String purchaseNo) { return ApiResponse.ok(service.submitPurchase(purchaseNo, OperatorResolver.currentOperator())); }
}
