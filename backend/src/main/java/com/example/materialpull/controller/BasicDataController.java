package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.BasicDataDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.BasicDataService;
import com.example.materialpull.service.LogExportService;
import com.example.materialpull.service.MappingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
    private final LogExportService logExportService;
    private final com.example.materialpull.service.DataScopeService dataScopeService;
    private final MappingQueryService mappingQueryService;

    private static final List<String> MAPPING_EXPORT_COLUMNS = List.of(
            "mappingOrder", "warehouseCode", "lineMaterialCode", "boxSize", "singleUnitUsage",
            "warehouseLocation", "deliveryAddress", "description", "quantity", "deliveryType",
            "remark", "deliveryArea", "warehouseMaterialCode", "factory");
    private static final List<String> MAPPING_EXPORT_HEADERS = List.of(
            "序号", "仓库代号", "物料号", "盒子大小", "单根用量", "仓库位置",
            "总装地址", "描述", "数量", "用途", "备注", "配送区域", "仓库料号", "工厂");

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
    @RequireRoles({UserRole.SUB_ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<?> mappings(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                                   @RequestParam(defaultValue = "") String keyword,
                                   @RequestParam(defaultValue = "") String filters) {
        if (page == null && size == null && filters.isBlank()) return ApiResponse.ok(service.mappings());
        int safePage = Math.max(page == null ? 0 : page, 0);
        int safeSize = Math.min(Math.max(size == null ? 50 : size, 10), 200);
        var result = filters.isBlank()
                ? service.mappings(keyword, PageRequest.of(safePage, safeSize))
                : mappingQueryService.page(keyword, filters, PageRequest.of(safePage, safeSize,
                    Sort.by("lineMaterialCode").and(Sort.by("mappingOrder")).and(Sort.by("id"))));
        return ApiResponse.ok(Map.of("items", result.getContent(), "total", result.getTotalElements()));
    }

    @GetMapping("/mappings/filter-options")
    @RequireRoles({UserRole.SUB_ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<String>> mappingFilterOptions(@RequestParam String field,
                                                           @RequestParam(defaultValue = "") String keyword,
                                                           @RequestParam(defaultValue = "") String filters,
                                                           @RequestParam(defaultValue = "") String search) {
        return ApiResponse.ok(mappingQueryService.options(field, keyword, filters, search));
    }

    @PostMapping("/mappings")
    @RequireRoles({UserRole.ADMIN, UserRole.SUB_ADMIN})
    public ApiResponse<MaterialMappingEntity> saveMapping(@RequestBody MaterialMappingEntity e) { return ApiResponse.ok(service.saveMapping(e)); }

    @DeleteMapping("/mappings/{id}")
    @RequireRoles({UserRole.ADMIN, UserRole.SUB_ADMIN})
    public ApiResponse<Void> delMapping(@PathVariable Long id) { service.deleteMapping(id); return ApiResponse.ok(null); }

    @GetMapping("/mappings/export")
    @RequireRoles({UserRole.ADMIN, UserRole.SUB_ADMIN})
    public ResponseEntity<byte[]> exportMappings() {
        byte[] body = logExportService.exportToXlsx("料号映射", service.exportMappings(), MAPPING_EXPORT_COLUMNS, MAPPING_EXPORT_HEADERS);
        String filename = logExportService.buildFileName("mappings");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, 200);
    }

    @DeleteMapping("/mappings")
    @RequireRoles({UserRole.ADMIN, UserRole.SUB_ADMIN})
    public ApiResponse<Map<String, Object>> deleteMappings(@RequestBody(required = false) MappingDeleteRequest req) {
        if (req == null) req = new MappingDeleteRequest();
        return ApiResponse.ok(service.deleteMappings(req.scope, req.deliveryArea, req.ids));
    }

    public static class MappingDeleteRequest {
        /** ALL=全部；AREA=按配送区域；其余按 ids 删除。 */
        public String scope;
        public String deliveryArea;
        public List<Long> ids;
    }

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
    public ApiResponse<List<BoxEntity>> boxes() { return ApiResponse.ok(service.boxes()); }

    @GetMapping("/inventory")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<InventoryEntity>> inventory() { return ApiResponse.ok(service.inventory()); }

    @PostMapping("/inventory")
    @RequireRoles({UserRole.WAREHOUSE})
    public ApiResponse<InventoryEntity> saveInventory(@RequestBody BasicDataDtos.InventorySaveRequest req) { return ApiResponse.ok(service.saveInventory(req)); }

    @DeleteMapping("/inventory/{id}")
    @RequireRoles({UserRole.WAREHOUSE})
    public ApiResponse<Void> delInventory(@PathVariable Long id) { service.deleteInventory(id); return ApiResponse.ok(null); }

    @GetMapping("/users")
    @RequireRoles({UserRole.ADMIN, UserRole.SUB_ADMIN})
    public ApiResponse<List<BasicDataDtos.UserResponse>> users() { return ApiResponse.ok(service.users()); }

    @PostMapping("/users")
    @RequireRoles({UserRole.ADMIN, UserRole.SUB_ADMIN})
    public ApiResponse<BasicDataDtos.UserResponse> saveUser(@RequestBody BasicDataDtos.UserRequest req) { return ApiResponse.ok(service.saveUser(req)); }

    @DeleteMapping("/users/{id}")
    @RequireRoles({UserRole.ADMIN, UserRole.SUB_ADMIN})
    public ApiResponse<Void> delUser(@PathVariable Long id) { service.deleteUser(id); return ApiResponse.ok(null); }

    @GetMapping("/users/scope-options")
    @RequireRoles({UserRole.ADMIN, UserRole.SUB_ADMIN})
    public ApiResponse<Map<String, Object>> userScopeOptions() { return ApiResponse.ok(service.userScopeOptions()); }

    @GetMapping("/configs")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<List<SystemConfigEntity>> configs() {
        dataScopeService.requireGlobalAdmin();
        return ApiResponse.ok(configRepository.findAll(topPage()).getContent());
    }

    @PostMapping("/configs")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<SystemConfigEntity> saveConfig(@RequestBody BasicDataDtos.ConfigRequest req) { return ApiResponse.ok(service.saveConfig(req)); }

    private PageRequest topPage() { return PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "id")); }
}
