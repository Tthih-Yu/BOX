package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.BasicDataDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.BasicDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class BasicDataController {
    private final MaterialRepository materialRepository;
    private final MaterialMappingRepository mappingRepository;
    private final StationMaterialRepository stationMaterialRepository;
    private final BoxRepository boxRepository;
    private final InventoryRepository inventoryRepository;
    private final SystemConfigRepository configRepository;
    private final BasicDataService service;

    @GetMapping("/materials")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
    public ApiResponse<List<MaterialEntity>> materials() { return ApiResponse.ok(materialRepository.findAll(topPage()).getContent()); }

    @PostMapping("/materials")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<MaterialEntity> saveMaterial(@RequestBody MaterialEntity e) { return ApiResponse.ok(service.saveMaterial(e)); }

    @DeleteMapping("/materials/{id}")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<Void> delMaterial(@PathVariable Long id) { service.disableMaterial(id); return ApiResponse.ok(null); }

    @GetMapping("/mappings")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<MaterialMappingEntity>> mappings() { return ApiResponse.ok(mappingRepository.findAll(topPage()).getContent()); }

    @PostMapping("/mappings")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<MaterialMappingEntity> saveMapping(@RequestBody MaterialMappingEntity e) { return ApiResponse.ok(service.saveMapping(e)); }

    @DeleteMapping("/mappings/{id}")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<Void> delMapping(@PathVariable Long id) { service.disableMapping(id); return ApiResponse.ok(null); }

    @GetMapping("/station-materials")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
    public ApiResponse<List<StationMaterialEntity>> stationMaterials() { return ApiResponse.ok(stationMaterialRepository.findAll(topPage()).getContent()); }

    @PostMapping("/station-materials")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<StationMaterialEntity> saveStationMaterial(@RequestBody StationMaterialEntity e) { return ApiResponse.ok(service.saveStationMaterial(e)); }

    @DeleteMapping("/station-materials/{id}")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<Void> delStationMaterial(@PathVariable Long id) { service.disableStationMaterial(id); return ApiResponse.ok(null); }

    @GetMapping("/boxes")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
    public ApiResponse<List<BoxEntity>> boxes() { return ApiResponse.ok(boxRepository.findAll(topPage()).getContent()); }

    @GetMapping("/inventory")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<InventoryEntity>> inventory() { return ApiResponse.ok(inventoryRepository.findAll(topPage()).getContent()); }

    @PostMapping("/inventory")
    @RequireRoles({UserRole.WAREHOUSE})
    public ApiResponse<InventoryEntity> saveInventory(@RequestBody BasicDataDtos.InventorySaveRequest req) { return ApiResponse.ok(service.saveInventory(req)); }

    @DeleteMapping("/inventory/{id}")
    @RequireRoles({UserRole.WAREHOUSE})
    public ApiResponse<Void> delInventory(@PathVariable Long id) { service.deleteInventory(id); return ApiResponse.ok(null); }

    @GetMapping("/users")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<List<BasicDataDtos.UserResponse>> users() { return ApiResponse.ok(service.users()); }

    @PostMapping("/users")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<BasicDataDtos.UserResponse> saveUser(@RequestBody BasicDataDtos.UserRequest req) { return ApiResponse.ok(service.saveUser(req)); }

    @DeleteMapping("/users/{id}")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<Void> delUser(@PathVariable Long id) { service.deleteUser(id); return ApiResponse.ok(null); }

    @GetMapping("/configs")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<List<SystemConfigEntity>> configs() { return ApiResponse.ok(configRepository.findAll(topPage()).getContent()); }

    @PostMapping("/configs")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<SystemConfigEntity> saveConfig(@RequestBody BasicDataDtos.ConfigRequest req) { return ApiResponse.ok(service.saveConfig(req)); }

    private PageRequest topPage() { return PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "id")); }
}
