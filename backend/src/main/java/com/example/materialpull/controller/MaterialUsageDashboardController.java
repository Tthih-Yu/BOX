package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.MaterialUsageDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/material-usage-dashboard")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
public class MaterialUsageDashboardController {
    private final MaterialUsageDashboardService service;

    @GetMapping
    public ApiResponse<Map<String, Object>> dashboard(@RequestParam(required = false) String from,
                                                       @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.dashboard(from, to));
    }
}
