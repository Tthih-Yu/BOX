package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.PrintJobEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.PrintJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/print-jobs")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE, UserRole.PLANNER, UserRole.VIEWER})
public class PrintJobController {
    private final PrintJobService service;

    @GetMapping
    public ApiResponse<List<PrintJobEntity>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(status));
    }

    @PostMapping
    @RequireRoles({UserRole.WAREHOUSE})
    public ApiResponse<PrintJobEntity> create(@RequestBody FactoryDtos.PrintRequest req) {
        return ApiResponse.ok(service.createForTask(req));
    }

    @PostMapping("/callback")
    @RequireRoles({UserRole.SYSTEM})
    public ApiResponse<PrintJobEntity> callback(@RequestBody FactoryDtos.PrintCallbackRequest req) {
        return ApiResponse.ok(service.callback(req));
    }
}
