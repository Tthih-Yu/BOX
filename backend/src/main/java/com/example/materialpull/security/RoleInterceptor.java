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
        if (current == UserRole.ADMIN || current == UserRole.SYSTEM) return true;
        if (Arrays.stream(required.value()).anyMatch(role -> role == current)) return true;
        throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色无权访问该功能");
    }
}
