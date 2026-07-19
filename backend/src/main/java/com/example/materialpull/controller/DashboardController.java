package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.DashboardDtos;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
public class DashboardController {
    private final DashboardService service;
    @GetMapping public ApiResponse<DashboardDtos.Dashboard> dashboard() { return ApiResponse.ok(service.dashboard()); }
}
