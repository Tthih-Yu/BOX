package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.ScanDtos;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/scan")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE, UserRole.LINE})
public class ScanController {
    private final ScanService scanService;

    @PostMapping("/empty")
    public ApiResponse<ScanDtos.ScanResult> empty(@RequestBody(required = false) ScanDtos.ScanRequest req, @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        fillIdempotency(req, idempotencyKey);
        return ApiResponse.ok(scanService.scanEmpty(req));
    }

    @PostMapping("/receive")
    public ApiResponse<ScanDtos.ScanResult> receive(@RequestBody(required = false) ScanDtos.ScanRequest req, @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        fillIdempotency(req, idempotencyKey);
        return ApiResponse.ok(scanService.scanReceive(req));
    }

    @PostMapping("/exception")
    public ApiResponse<ScanDtos.ScanResult> exception(@RequestBody(required = false) ScanDtos.ScanRequest req, @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        fillIdempotency(req, idempotencyKey);
        return ApiResponse.ok(scanService.scanException(req));
    }

    private void fillIdempotency(ScanDtos.ScanRequest req, String idempotencyKey) {
        if (req == null) return;
        if (req.idempotencyKey == null || req.idempotencyKey.isBlank()) {
            req.idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? RequestContext.getTraceId() : idempotencyKey;
        }
    }
}
