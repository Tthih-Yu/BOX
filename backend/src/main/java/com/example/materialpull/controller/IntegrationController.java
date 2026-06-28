package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
public class IntegrationController {
    private final IntegrationService service;

    @GetMapping("/links")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<SapImsLinkEntity>> links(@RequestParam(required = false) String systemCode) { return ApiResponse.ok(service.links(systemCode)); }

    @PostMapping("/links")
    @RequireRoles({UserRole.ADMIN, UserRole.SYSTEM})
    public ApiResponse<SapImsLinkEntity> link(@RequestBody FactoryDtos.IntegrationPayload payload) { return ApiResponse.ok(service.upsertLink(payload)); }

    @PostMapping("/sap/bom")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.SYSTEM})
    public ApiResponse<MaterialBomEntity> sapBom(@RequestBody FactoryDtos.IntegrationPayload payload) { return ApiResponse.ok(service.receiveSapBom(payload)); }

    @PostMapping("/ims/inventory")
    @RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE, UserRole.SYSTEM})
    public ApiResponse<InventoryEntity> imsInventory(@RequestBody FactoryDtos.IntegrationPayload payload) { return ApiResponse.ok(service.receiveImsInventory(payload)); }

    @PostMapping("/ppc/plan")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.SYSTEM})
    public ApiResponse<ProductionPlanEntity> ppcPlan(@RequestBody FactoryDtos.IntegrationPayload payload) { return ApiResponse.ok(service.receivePpcPlan(payload)); }
}
