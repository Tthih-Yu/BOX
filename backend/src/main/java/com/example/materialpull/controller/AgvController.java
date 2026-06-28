package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.AgvJobEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.AgvService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agv-jobs")
@RequiredArgsConstructor
public class AgvController {
    private final AgvService service;

    @GetMapping
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER, UserRole.SYSTEM})
    public ApiResponse<List<AgvJobEntity>> list(@RequestParam(required = false) String status) { return ApiResponse.ok(service.list(status)); }

    @PostMapping("/dispatch")
    @RequireRoles({UserRole.WAREHOUSE, UserRole.SYSTEM})
    public ApiResponse<AgvJobEntity> dispatch(@RequestBody FactoryDtos.AgvDispatchRequest req) { return ApiResponse.ok(service.dispatch(req)); }

    @PostMapping("/callback")
    @RequireRoles({UserRole.SYSTEM})
    public ApiResponse<AgvJobEntity> callback(@RequestBody FactoryDtos.AgvCallbackRequest req) {
        return ApiResponse.ok(service.callback(req));
    }

    @PostMapping("/{agvJobNo}/callback")
    @RequireRoles({UserRole.SYSTEM})
    public ApiResponse<AgvJobEntity> callbackByPath(@PathVariable String agvJobNo, @RequestParam String status, @RequestParam(required = false) String externalJobNo, @RequestParam(required = false) String message) {
        return ApiResponse.ok(service.callback(agvJobNo, status, externalJobNo, message));
    }
}
