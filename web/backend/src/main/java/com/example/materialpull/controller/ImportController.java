package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.entity.ImportBatchEntity;
import com.example.materialpull.entity.ImportErrorEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.ImportBatchRepository;
import com.example.materialpull.repository.ImportErrorRepository;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/imports")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
public class ImportController {
    private final ImportService importService;
    private final ImportBatchRepository batchRepository;
    private final ImportErrorRepository errorRepository;

    @PostMapping("/{type}")
    public ApiResponse<ImportBatchEntity> upload(@PathVariable String type, @RequestParam MultipartFile file) throws Exception {
        return ApiResponse.ok(importService.importExcel(type, file, OperatorResolver.currentOperator()));
    }

    @GetMapping public ApiResponse<List<ImportBatchEntity>> batches() { return ApiResponse.ok(batchRepository.findAll(PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "id"))).getContent()); }
    @GetMapping("/{batchNo}/errors") public ApiResponse<List<ImportErrorEntity>> errors(@PathVariable String batchNo) { return ApiResponse.ok(errorRepository.findByBatchNoOrderByRowNoAsc(batchNo)); }
}
