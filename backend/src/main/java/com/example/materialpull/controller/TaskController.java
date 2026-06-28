package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.TaskActionRequest;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
public class TaskController {
    private final TaskService service;

    @GetMapping
    public ApiResponse<List<ReplenishmentTaskEntity>> list(@RequestParam(required = false) String status) { return ApiResponse.ok(service.list(status)); }

    @DeleteMapping("/{taskNo}")
    @RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE})
    public ApiResponse<Void> delete(@PathVariable String taskNo) {
        service.deleteTask(taskNo);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{taskNo}/{action}")
    @RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE, UserRole.LINE})
    public ApiResponse<ReplenishmentTaskEntity> action(@PathVariable String taskNo,
                                                       @PathVariable String action,
                                                       @RequestBody(required = false) TaskActionRequest req,
                                                       @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        if (req == null) req = new TaskActionRequest();
        if (req.requestId == null || req.requestId.isBlank()) {
            req.requestId = idempotencyKey == null || idempotencyKey.isBlank() ? RequestContext.getTraceId() : idempotencyKey;
        }
        return ApiResponse.ok(service.action(taskNo, action, req));
    }
}
