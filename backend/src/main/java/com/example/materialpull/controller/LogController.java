package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.LogExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
public class LogController {
    private final ScanLogRepository scanLogRepository;
    private final TaskLogRepository taskLogRepository;
    private final PrintLogRepository printLogRepository;
    private final InterfaceLogRepository interfaceLogRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final LogExportService logExportService;

    private static final List<String> SCAN_COLUMNS = List.of(
            "id", "labelCode", "boxCode", "action", "success", "message", "operator", "deviceNo", "stationCode", "materialCode", "scanAt");
    private static final List<String> TASK_COLUMNS = List.of(
            "id", "taskNo", "action", "fromStatus", "toStatus", "operator", "message", "createdAt");
    private static final List<String> PRINT_COLUMNS = List.of(
            "id", "labelCode", "action", "operator", "printerName", "success", "message", "createdAt");
    private static final List<String> INTERFACE_COLUMNS = List.of(
            "id", "interfaceName", "direction", "requestBody", "responseBody", "success", "message", "createdAt");

    @GetMapping("/scans")
    public ApiResponse<?> scans(@RequestParam(required = false) String labelCode,
                                @RequestParam(required = false) String taskNo) {
        return ApiResponse.ok(loadScans(labelCode, taskNo));
    }

    @GetMapping("/tasks") public ApiResponse<?> tasks() { return ApiResponse.ok(taskLogRepository.findTop1000ByOrderByCreatedAtDesc()); }
    @GetMapping("/prints") public ApiResponse<?> prints() { return ApiResponse.ok(printLogRepository.findTop1000ByOrderByCreatedAtDesc()); }
    @GetMapping("/interfaces") public ApiResponse<?> interfaces() { return ApiResponse.ok(interfaceLogRepository.findTop1000ByOrderByCreatedAtDesc()); }

    @GetMapping("/scans/export")
    public ResponseEntity<byte[]> exportScans(@RequestParam(required = false) String labelCode,
                                              @RequestParam(required = false) String taskNo) {
        return buildExcelResponse("scan-logs", logExportService.exportToXlsx("扫码日志", loadScans(labelCode, taskNo), SCAN_COLUMNS));
    }

    @GetMapping("/tasks/export")
    public ResponseEntity<byte[]> exportTasks() {
        return buildExcelResponse("task-logs", logExportService.exportToXlsx("任务日志", taskLogRepository.findTop1000ByOrderByCreatedAtDesc(), TASK_COLUMNS));
    }

    @GetMapping("/prints/export")
    public ResponseEntity<byte[]> exportPrints() {
        return buildExcelResponse("print-logs", logExportService.exportToXlsx("打印日志", printLogRepository.findTop1000ByOrderByCreatedAtDesc(), PRINT_COLUMNS));
    }

    @GetMapping("/interfaces/export")
    public ResponseEntity<byte[]> exportInterfaces() {
        return buildExcelResponse("interface-logs", logExportService.exportToXlsx("接口日志", interfaceLogRepository.findTop1000ByOrderByCreatedAtDesc(), INTERFACE_COLUMNS));
    }

    @DeleteMapping("/scans")
    @RequireRoles(UserRole.ADMIN)
    @Transactional
    public ApiResponse<Map<String, Object>> deleteScans(@RequestBody(required = false) DeleteRequest req) {
        return ApiResponse.ok(performDelete(req, scanLogRepository));
    }

    @DeleteMapping("/tasks")
    @RequireRoles(UserRole.ADMIN)
    @Transactional
    public ApiResponse<Map<String, Object>> deleteTasks(@RequestBody(required = false) DeleteRequest req) {
        return ApiResponse.ok(performDelete(req, taskLogRepository));
    }

    @DeleteMapping("/prints")
    @RequireRoles(UserRole.ADMIN)
    @Transactional
    public ApiResponse<Map<String, Object>> deletePrints(@RequestBody(required = false) DeleteRequest req) {
        return ApiResponse.ok(performDelete(req, printLogRepository));
    }

    @DeleteMapping("/interfaces")
    @RequireRoles(UserRole.ADMIN)
    @Transactional
    public ApiResponse<Map<String, Object>> deleteInterfaces(@RequestBody(required = false) DeleteRequest req) {
        return ApiResponse.ok(performDelete(req, interfaceLogRepository));
    }

    public static class DeleteRequest {
        public List<Long> ids;
        /** 为 true 时清空整张日志表，仅管理员可用。 */
        public Boolean all;
    }

    private List<?> loadScans(String labelCode, String taskNo) {
        if (taskNo != null && !taskNo.isBlank()) {
            ReplenishmentTaskEntity task = taskRepository.findByTaskNo(taskNo.trim()).orElse(null);
            if (task == null) return Collections.emptyList();
            String label = firstNonBlank(task.getSourceLabelCode(), task.getMaterialCode(), task.getWarehouseCode());
            if (label == null) return Collections.emptyList();
            return scanLogRepository.findTop100ByLabelCodeOrderByScanAtDesc(label);
        }
        if (labelCode != null && !labelCode.isBlank()) {
            return scanLogRepository.findTop100ByLabelCodeOrderByScanAtDesc(labelCode.trim());
        }
        return scanLogRepository.findTop1000ByOrderByScanAtDesc();
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

    private ResponseEntity<byte[]> buildExcelResponse(String prefix, byte[] body) {
        String filename = logExportService.buildFileName(prefix);
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, 200);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }
}
