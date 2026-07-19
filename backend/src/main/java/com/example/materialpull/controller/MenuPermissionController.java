package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.MenuPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/menu-permissions")
@RequiredArgsConstructor
public class MenuPermissionController {
    private final MenuPermissionService service;

    /** 当前登录用户可见菜单白名单；null 表示走前端默认角色映射(不做后台限制)。 */
    @GetMapping("/mine")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
    public ApiResponse<List<String>> mine() {
        return ApiResponse.ok(service.menusForRole(RequestContext.getRole()));
    }

    @GetMapping
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<Map<String, List<String>>> all() {
        return ApiResponse.ok(service.readAll());
    }

    @PostMapping
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<Void> save(@RequestBody Map<String, List<String>> permissions) {
        service.saveAll(permissions);
        return ApiResponse.ok(null);
    }
}
