package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.entity.ImportBatchEntity;
import com.example.materialpull.entity.ImportErrorEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.ImportBatchRepository;
import com.example.materialpull.repository.ImportErrorRepository;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.ImportService;
import com.example.materialpull.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/imports")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.SUB_ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
public class ImportController {
    private final ImportService importService;
    private final ImportBatchRepository batchRepository;
    private final ImportErrorRepository errorRepository;
    private final DataScopeService dataScopeService;

    @PostMapping("/{type}")
    public ApiResponse<ImportBatchEntity> upload(@PathVariable String type,
                                                 @RequestParam MultipartFile file,
                                                 @RequestParam(value = "overwrite", required = false, defaultValue = "false") boolean overwrite) throws Exception {
        String normalizedType = type == null ? "" : type.trim();
        // 料号映射导入（包括覆盖上传）仅限两级管理员；普通管理员只操作所属工厂。
        if ("mappings".equals(normalizedType)) {
            RequestContext.requireAnyRole(UserRole.ADMIN, UserRole.SUB_ADMIN);
        } else if (RequestContext.getRole() == UserRole.SUB_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "普通管理员只能导入料号映射");
        }
        return ApiResponse.ok(importService.importExcel(normalizedType, file, OperatorResolver.currentOperator(), overwrite));
    }

    @GetMapping public ApiResponse<List<ImportBatchEntity>> batches() {
        dataScopeService.requireGlobalAdmin();
        return ApiResponse.ok(batchRepository.findAll(PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "id"))).getContent());
    }

    @GetMapping("/{batchNo}/errors") public ApiResponse<List<ImportErrorEntity>> errors(@PathVariable String batchNo) {
        if (!dataScopeService.isGlobalAdmin()) {
            RequestContext.requireAnyRole(UserRole.SUB_ADMIN);
            ImportBatchEntity batch = batchRepository.findByBatchNo(batchNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入批次不存在：" + batchNo));
            boolean ownMappingBatch = "mappings".equals(batch.getImportType())
                    && OperatorResolver.currentOperator().equals(batch.getOperator());
            if (!ownMappingBatch) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "普通管理员只能查看本人料号映射导入的错误明细");
            }
        }
        return ApiResponse.ok(errorRepository.findByBatchNoOrderByRowNoAsc(batchNo));
    }
}
