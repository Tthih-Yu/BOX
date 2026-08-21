package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.entity.ImportErrorEntity;
import com.example.materialpull.entity.SimpleBomBatchEntity;
import com.example.materialpull.entity.SimpleBomEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.ImportErrorRepository;
import com.example.materialpull.repository.SimpleBomRepository;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.SimpleBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/simple-bom")
@RequiredArgsConstructor
public class SimpleBomController {
    private final SimpleBomService service;
    private final SimpleBomRepository bomRepository;
    private final ImportErrorRepository errorRepository;

    /** CSV 高速导入为新批次。activate=true 时校验通过立即切换为当前有效批次。 */
    @PostMapping("/import")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<SimpleBomBatchEntity> importBom(@RequestParam MultipartFile file,
                                                       @RequestParam(value = "activate", required = false, defaultValue = "false") boolean activate) {
        return ApiResponse.ok(service.importBom(file, activate, OperatorResolver.currentOperator()));
    }

    /** 批次列表(倒序)。 */
    @GetMapping("/batches")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<SimpleBomBatchEntity>> batches() { return ApiResponse.ok(service.listBatches()); }

    /** 当前有效批次。 */
    @GetMapping("/active")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<SimpleBomBatchEntity> active() { return ApiResponse.ok(service.activeBatch()); }

    /** 激活/回滚：把指定批次切换为当前有效批次，原有效批次归档。 */
    @PostMapping("/batches/{batchNo}/activate")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<SimpleBomBatchEntity> activate(@PathVariable String batchNo) {
        return ApiResponse.ok(service.activate(batchNo, OperatorResolver.currentOperator()));
    }

    @DeleteMapping("/batches/{batchNo}")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<Void> delete(@PathVariable String batchNo) { service.deleteBatch(batchNo); return ApiResponse.ok(null); }

    /** 单条新增/编辑 BOM 行(归属当前有效批次)。id 空=新增。 */
    @PostMapping("/rows")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<SimpleBomEntity> saveRow(@RequestBody SimpleBomEntity req) {
        return ApiResponse.ok(service.saveRow(req.getId(), req.getMaterialCode(), req.getComponentCode()));
    }

    /** 删除单条 BOM 行(仅限当前有效批次)。 */
    @DeleteMapping("/rows/{id}")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<Void> deleteRow(@PathVariable Long id) { service.deleteRow(id); return ApiResponse.ok(null); }

    /** 导入错误明细。 */
    @GetMapping("/batches/{batchNo}/errors")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<ImportErrorEntity>> errors(@PathVariable String batchNo) {
        return ApiResponse.ok(errorRepository.findByBatchNoOrderByRowNoAsc(batchNo));
    }

    /**
     * 分页查询某批次的 BOM 明细；batchNo 缺省时查当前有效批次。可按物料8D号过滤。
     */
    @GetMapping("/rows")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<Map<String, Object>> rows(@RequestParam(required = false) String batchNo,
                                                  @RequestParam(required = false) String materialCode,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        String effectiveBatch = batchNo;
        if (effectiveBatch == null || effectiveBatch.isBlank()) {
            SimpleBomBatchEntity active = service.activeBatch();
            effectiveBatch = active == null ? null : active.getBatchNo();
        }
        if (effectiveBatch == null) {
            return ApiResponse.ok(Map.of("batchNo", "", "total", 0L, "rows", List.of()));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500), Sort.by(Sort.Direction.ASC, "materialCode"));
        Page<SimpleBomEntity> result = (materialCode == null || materialCode.isBlank())
                ? bomRepository.findByBatchNo(effectiveBatch, pageable)
                : bomRepository.findByBatchNoAndMaterialCode(effectiveBatch, materialCode.trim(), pageable);
        return ApiResponse.ok(Map.of(
                "batchNo", effectiveBatch,
                "total", result.getTotalElements(),
                "rows", result.getContent()));
    }

    /** 清空当前有效批次的全部 BOM 行。 */
    @DeleteMapping("/rows")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<Map<String, Integer>> deleteAllRows() {
        int deleted = service.deleteAllRows();
        return ApiResponse.ok(Map.of("deleted", deleted));
    }

    /** 批量删除：上传 CSV/Excel，精确匹配物料8D+组件8D后删除。 */
    @PostMapping("/delete-by-file")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<Map<String, Integer>> deleteByFile(@RequestParam MultipartFile file) {
        return ApiResponse.ok(service.deleteByFile(file));
    }
}
