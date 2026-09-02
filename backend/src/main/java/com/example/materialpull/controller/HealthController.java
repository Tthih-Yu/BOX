package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.maintenance.HealthDtos;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.maintenance.DataQualityService;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {
    private final DataQualityService dataQualityService;
    private final DataScopeService dataScopeService;

    @GetMapping("/ready")
    public ApiResponse<Map<String, Object>> ready() {
        return ApiResponse.ok(Map.of("status", "UP", "checkedAt", LocalDateTime.now(), "service", "material-pull-system"));
    }

    @GetMapping("/factory")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
    public ApiResponse<HealthDtos.HealthReport> factory(@RequestParam(defaultValue = "false") boolean createAlerts) {
        dataScopeService.requireGlobalAdmin();
        return ApiResponse.ok(dataQualityService.checkFactoryHealth(createAlerts));
    }
}
