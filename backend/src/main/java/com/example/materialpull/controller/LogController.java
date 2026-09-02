package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.PageResult;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import com.example.materialpull.service.DataScopeService;
import com.example.materialpull.service.LogExportService;
import com.example.materialpull.security.RequireRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
public class LogController {
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int DEFAULT_EXPORT_LIMIT = 1000;

    private final ScanLogRepository scanLogRepository;
    private final TaskLogRepository taskLogRepository;
    private final PrintLogRepository printLogRepository;
    private final InterfaceLogRepository interfaceLogRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final LogExportService logExportService;
    private final DataScopeService dataScopeService;

    private static final List<String> SCAN_COLUMNS = List.of(
            "id", "labelCode", "boxCode", "action", "success", "message", "operator", "deviceNo", "stationCode", "materialCode", "scanAt");
    private static final List<String> TASK_COLUMNS = List.of(
            "id", "taskNo", "action", "fromStatus", "toStatus", "operator", "message", "createdAt");
    private static final List<String> PRINT_COLUMNS = List.of(
            "id", "labelCode", "action", "operator", "printerName", "success", "message", "createdAt");
    private static final List<String> INTERFACE_COLUMNS = List.of(
            "id", "interfaceName", "direction", "requestBody", "responseBody", "success", "message", "createdAt");

    @GetMapping("/scans")
    public ApiResponse<PageResult<ScanLogEntity>> scans(@RequestParam(required = false) String labelCode,
                                                        @RequestParam(required = false) String taskNo,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "100") int size) {
        validateTimeRange(startAt, endAt);
        return ApiResponse.ok(toPageResult(scanLogRepository.findAll(scanSpecification(labelCode, taskNo, startAt, endAt),
                pageRequest(page, size, "scanAt"))));
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResult<TaskLogEntity>> tasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        dataScopeService.requireGlobalAdmin();
        validateTimeRange(startAt, endAt);
        return ApiResponse.ok(toPageResult(taskLogRepository.findAll(timeSpecification("createdAt", startAt, endAt),
                pageRequest(page, size, "createdAt"))));
    }

    @GetMapping("/prints")
    public ApiResponse<PageResult<PrintLogEntity>> prints(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        dataScopeService.requireGlobalAdmin();
        validateTimeRange(startAt, endAt);
        return ApiResponse.ok(toPageResult(printLogRepository.findAll(timeSpecification("createdAt", startAt, endAt),
                pageRequest(page, size, "createdAt"))));
    }

    @GetMapping("/interfaces")
    public ApiResponse<PageResult<InterfaceLogEntity>> interfaces(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        dataScopeService.requireGlobalAdmin();
        validateTimeRange(startAt, endAt);
        return ApiResponse.ok(toPageResult(interfaceLogRepository.findAll(timeSpecification("createdAt", startAt, endAt),
                pageRequest(page, size, "createdAt"))));
    }

    @GetMapping("/scans/export")
    public ResponseEntity<StreamingResponseBody> exportScans(@RequestParam(required = false) String labelCode,
                                                               @RequestParam(required = false) String taskNo,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        validateTimeRange(startAt, endAt);
        Specification<ScanLogEntity> spec = scanSpecification(labelCode, taskNo, startAt, endAt);
        return buildStreamingExcelResponse("scan-logs", "扫码日志", SCAN_COLUMNS, hasTimeRange(startAt, endAt),
                (page, size) -> scanLogRepository.findAll(spec,
                        pageRequest(page, size, "scanAt")));
    }

    @GetMapping("/tasks/export")
    public ResponseEntity<StreamingResponseBody> exportTasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        dataScopeService.requireGlobalAdmin();
        validateTimeRange(startAt, endAt);
        Specification<TaskLogEntity> spec = timeSpecification("createdAt", startAt, endAt);
        return buildStreamingExcelResponse("task-logs", "任务日志", TASK_COLUMNS, hasTimeRange(startAt, endAt),
                (page, size) -> taskLogRepository.findAll(spec, pageRequest(page, size, "createdAt")));
    }

    @GetMapping("/prints/export")
    public ResponseEntity<StreamingResponseBody> exportPrints(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        dataScopeService.requireGlobalAdmin();
        validateTimeRange(startAt, endAt);
        Specification<PrintLogEntity> spec = timeSpecification("createdAt", startAt, endAt);
        return buildStreamingExcelResponse("print-logs", "打印日志", PRINT_COLUMNS, hasTimeRange(startAt, endAt),
                (page, size) -> printLogRepository.findAll(spec, pageRequest(page, size, "createdAt")));
    }

    @GetMapping("/interfaces/export")
    public ResponseEntity<StreamingResponseBody> exportInterfaces(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        dataScopeService.requireGlobalAdmin();
        validateTimeRange(startAt, endAt);
        Specification<InterfaceLogEntity> spec = timeSpecification("createdAt", startAt, endAt);
        return buildStreamingExcelResponse("interface-logs", "接口日志", INTERFACE_COLUMNS, hasTimeRange(startAt, endAt),
                (page, size) -> interfaceLogRepository.findAll(spec, pageRequest(page, size, "createdAt")));
    }

    @DeleteMapping("/scans")
    @RequireRoles(UserRole.ADMIN)
    @Transactional
    public ApiResponse<Map<String, Object>> deleteScans(@RequestBody(required = false) DeleteRequest req) {
        dataScopeService.requireGlobalAdmin();
        return ApiResponse.ok(performDelete(req, scanLogRepository));
    }

    @DeleteMapping("/tasks")
    @RequireRoles(UserRole.ADMIN)
    @Transactional
    public ApiResponse<Map<String, Object>> deleteTasks(@RequestBody(required = false) DeleteRequest req) {
        dataScopeService.requireGlobalAdmin();
        return ApiResponse.ok(performDelete(req, taskLogRepository));
    }

    @DeleteMapping("/prints")
    @RequireRoles(UserRole.ADMIN)
    @Transactional
    public ApiResponse<Map<String, Object>> deletePrints(@RequestBody(required = false) DeleteRequest req) {
        dataScopeService.requireGlobalAdmin();
        return ApiResponse.ok(performDelete(req, printLogRepository));
    }

    @DeleteMapping("/interfaces")
    @RequireRoles(UserRole.ADMIN)
    @Transactional
    public ApiResponse<Map<String, Object>> deleteInterfaces(@RequestBody(required = false) DeleteRequest req) {
        dataScopeService.requireGlobalAdmin();
        return ApiResponse.ok(performDelete(req, interfaceLogRepository));
    }

    public static class DeleteRequest {
        public List<Long> ids;
        /** 为 true 时清空整张日志表，仅管理员可用。 */
        public Boolean all;
    }

    private Specification<ScanLogEntity> scanSpecification(String labelCode, String taskNo,
                                                            LocalDateTime startAt, LocalDateTime endAt) {
        String resolvedLabelCode = resolveScanLabelCode(labelCode, taskNo);
        boolean global = dataScopeService.isGlobalAdmin();
        String factory = global ? null : dataScopeService.currentFactory();
        List<String> areas = global ? List.of() : dataScopeService.currentDeliveryAreas();
        Specification<ScanLogEntity> specification = timeSpecification("scanAt", startAt, endAt);
        if (resolvedLabelCode != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("labelCode"), resolvedLabelCode));
        }
        if (global) return specification;
        if (factory == null || factory.isBlank() || areas == null || areas.isEmpty()) {
            return specification.and((root, query, cb) -> cb.disjunction());
        }
        String normalizedFactory = factory.trim().toLowerCase(Locale.ROOT);
        List<String> scopedAreas = List.copyOf(areas);
        return specification.and((root, query, cb) -> cb.and(
                cb.equal(cb.lower(root.get("factory")), normalizedFactory),
                root.get("deliveryArea").in(scopedAreas)));
    }

    private String resolveScanLabelCode(String labelCode, String taskNo) {
        if (taskNo != null && !taskNo.isBlank()) {
            ReplenishmentTaskEntity task = taskRepository.findByTaskNo(taskNo.trim()).orElse(null);
            if (task == null) return "__NO_MATCHING_TASK__";
            dataScopeService.requireAccessFactoryArea(task.getFactory(), task.getDeliveryArea());
            return firstNonBlank(task.getSourceLabelCode(), task.getMaterialCode(), task.getWarehouseCode(), "__NO_MATCHING_TASK__");
        }
        return labelCode == null || labelCode.isBlank() ? null : labelCode.trim();
    }

    private Map<String, Object> performDelete(DeleteRequest req, org.springframework.data.jpa.repository.JpaRepository<?, Long> repository) {
        if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        if (Boolean.TRUE.equals(req.all)) {
            long total = repository.count();
            repository.deleteAllInBatch();
            return Map.of("deleted", total, "mode", "ALL");
        }
        if (req.ids == null || req.ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要删除的日志记录");
        }
        List<Long> ids = req.ids.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要删除的日志记录");
        if (ids.size() > 2000) throw new BusinessException(ErrorCode.PARAM_ERROR, "单次最多删除 2000 条日志，请分批操作");
        repository.deleteAllByIdInBatch(ids);
        return Map.of("deleted", ids.size(), "mode", "IDS");
    }

    private <T> ResponseEntity<StreamingResponseBody> buildStreamingExcelResponse(String prefix, String sheetName,
                                                                                     List<String> columns, boolean exportAll,
                                                                                     LogExportService.PageFetcher<T> pageFetcher) {
        String filename = logExportService.buildFileName(prefix);
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        StreamingResponseBody body = output -> logExportService.exportPagedToXlsx(output, sheetName, columns, columns,
                exportAll ? 0 : DEFAULT_EXPORT_LIMIT, pageFetcher);
        return new ResponseEntity<>(body, headers, 200);
    }

    private PageRequest pageRequest(int page, int size, String timeProperty) {
        if (page < 0) throw new BusinessException(ErrorCode.PARAM_ERROR, "页码不能小于 0");
        int effectiveSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(page, effectiveSize, Sort.by(Sort.Order.desc(timeProperty), Sort.Order.desc("id")));
    }

    private void validateTimeRange(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束时间不能早于开始时间");
        }
    }

    private boolean hasTimeRange(LocalDateTime startAt, LocalDateTime endAt) {
        return startAt != null || endAt != null;
    }

    private <T> Specification<T> timeSpecification(String property, LocalDateTime startAt, LocalDateTime endAt) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (startAt != null) predicates.add(cb.greaterThanOrEqualTo(root.get(property), startAt));
            if (endAt != null) predicates.add(cb.lessThanOrEqualTo(root.get(property), endAt));
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private <T> PageResult<T> toPageResult(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }
}
