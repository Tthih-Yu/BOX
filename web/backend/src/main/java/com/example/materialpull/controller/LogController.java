package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import com.example.materialpull.security.RequireRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
public class LogController {
    private final ScanLogRepository scanLogRepository;
    private final TaskLogRepository taskLogRepository;
    private final PrintLogRepository printLogRepository;
    private final InterfaceLogRepository interfaceLogRepository;

    @GetMapping("/scans") public ApiResponse<?> scans() { return ApiResponse.ok(scanLogRepository.findTop1000ByOrderByScanAtDesc()); }
    @GetMapping("/tasks") public ApiResponse<?> tasks() { return ApiResponse.ok(taskLogRepository.findTop1000ByOrderByCreatedAtDesc()); }
    @GetMapping("/prints") public ApiResponse<?> prints() { return ApiResponse.ok(printLogRepository.findTop1000ByOrderByCreatedAtDesc()); }
    @GetMapping("/interfaces") public ApiResponse<?> interfaces() { return ApiResponse.ok(interfaceLogRepository.findTop1000ByOrderByCreatedAtDesc()); }
}
