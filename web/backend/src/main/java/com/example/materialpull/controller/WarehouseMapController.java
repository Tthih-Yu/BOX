package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.example.materialpull.security.RequireRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/warehouse-maps")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
public class WarehouseMapController {
    private final MaterialMappingRepository mappingRepository;

    @GetMapping
    public ApiResponse<List<MaterialMappingEntity>> list() {
        return ApiResponse.ok(mappingRepository.findAll());
    }
}
