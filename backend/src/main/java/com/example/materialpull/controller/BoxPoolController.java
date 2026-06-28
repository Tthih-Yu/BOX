package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.entity.BoxPoolEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.BoxPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/box-pool")
@RequiredArgsConstructor
@RequireRoles({UserRole.WAREHOUSE, UserRole.LINE, UserRole.PLANNER, UserRole.VIEWER})
public class BoxPoolController {
    private final BoxPoolService service;

    @GetMapping public ApiResponse<List<BoxPoolEntity>> list(@RequestParam(required = false) String status) { return ApiResponse.ok(service.list(status)); }

    @PostMapping
    @RequireRoles({UserRole.WAREHOUSE})
    public ApiResponse<BoxPoolEntity> save(@RequestBody BoxPoolEntity e) { return ApiResponse.ok(service.save(e)); }

    @PostMapping("/{containerNo}/return-empty")
    @RequireRoles({UserRole.WAREHOUSE, UserRole.LINE})
    public ApiResponse<BoxPoolEntity> returnEmpty(@PathVariable String containerNo, @RequestParam(required = false) String taskNo, @RequestParam(required = false) String location) {
        return ApiResponse.ok(service.returnEmpty(containerNo, taskNo, location, OperatorResolver.currentOperator()));
    }

    @PostMapping("/{containerNo}/back-warehouse")
    @RequireRoles({UserRole.WAREHOUSE})
    public ApiResponse<BoxPoolEntity> back(@PathVariable String containerNo, @RequestParam(required = false) String location) { return ApiResponse.ok(service.backToWarehouse(containerNo, location)); }
}
