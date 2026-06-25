package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.dto.maintenance.HealthDtos;
import com.example.materialpull.entity.SystemAlertEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.maintenance.DataQualityService;
import com.example.materialpull.maintenance.RecoveryService;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/maintenance")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN})
public class MaintenanceController {
    private final DataQualityService dataQualityService;
    private final RecoveryService recoveryService;
    private final SystemAlertService alertService;

    @GetMapping("/data-check")
    public ApiResponse<HealthDtos.HealthReport> dataCheck(@RequestParam(defaultValue = "false") boolean createAlerts) { return ApiResponse.ok(dataQualityService.checkFactoryHealth(createAlerts)); }

    @PostMapping("/recover/stuck-tasks")
    public ApiResponse<HealthDtos.RecoveryResult> recoverStuck(@RequestBody(required = false) Map<String, String> body) {
        String operator = OperatorResolver.currentOperator();
        return ApiResponse.ok(recoveryService.markStuckTasks(operator));
    }

    @PostMapping("/recover/inventory-available")
    public ApiResponse<HealthDtos.RecoveryResult> rebuildInventory(@RequestBody(required = false) Map<String, String> body) {
        String operator = OperatorResolver.currentOperator();
        return ApiResponse.ok(recoveryService.rebuildInventoryAvailable(operator));
    }

    @PostMapping("/recover/box-pairs")
    public ApiResponse<HealthDtos.RecoveryResult> lockBrokenPairs(@RequestBody(required = false) Map<String, String> body) {
        String operator = OperatorResolver.currentOperator();
        return ApiResponse.ok(recoveryService.lockBrokenPairs(operator));
    }

    @GetMapping("/alerts") public ApiResponse<List<SystemAlertEntity>> alerts() { return ApiResponse.ok(alertService.openAlerts()); }

    @PostMapping("/alerts/{id}/close")
    public ApiResponse<SystemAlertEntity> closeAlert(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String operator = OperatorResolver.currentOperator();
        String remark = body == null ? "已处理" : body.getOrDefault("remark", "已处理");
        return ApiResponse.ok(alertService.close(id, operator, remark));
    }
}
