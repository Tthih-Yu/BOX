package com.example.materialpull.security;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class RoleInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;
        RequireRoles required = method.getMethodAnnotation(RequireRoles.class);
        if (required == null) required = method.getBeanType().getAnnotation(RequireRoles.class);
        if (required == null) return true;

        UserRole current = RequestContext.getRole();
        if (current == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        // ADMIN 是人工最高权限账号；SYSTEM 仅允许访问显式声明 SYSTEM 的机器接口，
        // 禁止外部 API Key 借 SYSTEM 身份绕过所有业务角色限制。
        if (current == UserRole.ADMIN) return true;
        if (Arrays.stream(required.value()).anyMatch(role -> role == current)) return true;
        // 普通管理员(SUB_ADMIN)是“准管理员”：除管理员专属接口(仅标注 ADMIN)外全部放行。
        // 用户管理、系统参数、菜单权限配置等敏感接口都是 ADMIN-only，因此 SUB_ADMIN 无法借此提权。
        if (current == UserRole.SUB_ADMIN && !isAdminOnly(required)) return true;
        throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色无权访问该功能");
    }

    /** 判断该接口是否为管理员专属（@RequireRoles 只包含 ADMIN）。 */
    private boolean isAdminOnly(RequireRoles required) {
        UserRole[] roles = required.value();
        return roles.length == 1 && roles[0] == UserRole.ADMIN;
    }
}
