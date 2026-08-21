package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.BasicDataDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.BasicDataService;
import com.example.materialpull.service.LogExportService;
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

    private static final List<String> MAPPING_EXPORT_COLUMNS = List.of(
            "mappingOrder", "lineMaterialCode", "warehouseCode", "boxSize", "quantity",
            "deliveryType", "warehouseLocation", "deliveryAddress", "remark", "deliveryArea", "warehouseMaterialCode", "singleUnitUsage", "factory");
    private static final List<String> MAPPING_EXPORT_HEADERS = List.of(
            "序号", "物料号", "仓库代号", "盒子大小", "数量", "用途", "仓库位置",
            "总装地址", "备注", "配送区域", "仓库料号", "单根用量", "工厂");

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
    public ApiResponse<?> mappings(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, @RequestParam(defaultValue = "") String keyword) {
        if (page == null && size == null) return ApiResponse.ok(mappingRepository.findAllByOrderByLineMaterialCodeAscMappingOrderAscIdAsc());
        int safePage = Math.max(page == null ? 0 : page, 0);
        int safeSize = Math.min(Math.max(size == null ? 50 : size, 10), 200);
        String query = keyword == null ? "" : keyword.trim();
        var result = mappingRepository.findByLineMaterialCodeContainingIgnoreCaseOrWarehouseCodeContainingIgnoreCaseOrWarehouseMaterialCodeContainingIgnoreCaseOrDeliveryAddressContainingIgnoreCaseOrderByLineMaterialCodeAscMappingOrderAscIdAsc(query, query, query, query, PageRequest.of(safePage, safeSize));
        return ApiResponse.ok(Map.of("items", result.getContent(), "total", result.getTotalElements()));
    }

    @PostMapping("/mappings")
    @RequireRoles({UserRole.SUB_ADMIN, UserRole.PLANNER})
    public ApiResponse<MaterialMappingEntity> saveMapping(@RequestBody MaterialMappingEntity e) { return ApiResponse.ok(service.saveMapping(e)); }

    @DeleteMapping("/mappings/{id}")
    @RequireRoles({UserRole.SUB_ADMIN, UserRole.PLANNER})
    public ApiResponse<Void> delMapping(@PathVariable Long id) { service.disableMapping(id); return ApiResponse.ok(null); }

    @GetMapping("/mappings/export")
    @RequireRoles({UserRole.SUB_ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
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
    @RequireRoles({UserRole.SUB_ADMIN, UserRole.PLANNER})
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
