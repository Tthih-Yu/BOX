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
import java.util.Collection;
import java.util.List;

@Component
public class RoleInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;
        RequireRoles required = method.getMethodAnnotation(RequireRoles.class);
        if (required == null) required = method.getBeanType().getAnnotation(RequireRoles.class);
        if (required == null) return true;

        restoreAsyncRequestContext(request);
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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // StreamingResponseBody 完成后会发起一次 ASYNC dispatcher。该 dispatcher 使用的
        // Tomcat 工作线程没有 TraceIdFilter 的 finally 清理，必须主动释放 ThreadLocal。
        if (request != null && request.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC) {
            RequestContext.clear();
        }
    }

    /**
     * StreamingResponseBody 的完成阶段会通过 Servlet ASYNC dispatcher 再次经过 MVC 拦截器。
     * 认证过滤器在原始请求结束时已经清除了 ThreadLocal，但认证结果已由过滤器写入 request
     * attribute；这里仅在该受信任的 ASYNC dispatcher 中恢复它，避免将已提交的 Excel 响应
     * 错误地改写为 401，从而造成不完整的 chunked 响应。
     */
    private void restoreAsyncRequestContext(HttpServletRequest request) {
        if (request == null || request.getDispatcherType() != jakarta.servlet.DispatcherType.ASYNC
                || RequestContext.getRole() != null) {
            return;
        }
        Object roleValue = request.getAttribute("loginRole");
        if (!(roleValue instanceof String roleName) || roleName.isBlank()) return;

        final UserRole role;
        try {
            role = UserRole.valueOf(roleName);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        Object userIdValue = request.getAttribute("loginUserId");
        Long userId = userIdValue instanceof Number number ? number.longValue() : null;
        String username = attributeText(request, "loginUser");
        String realName = attributeText(request, "loginRealName");
        String factory = attributeText(request, "loginFactory");
        RequestContext.setLoginUser(userId, username, realName, role, factory,
                stringList(request.getAttribute("loginDeliveryAreas")));
    }

    private String attributeText(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String text ? text : null;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    /** 判断该接口是否为管理员专属（@RequireRoles 只包含 ADMIN）。 */
    private boolean isAdminOnly(RequireRoles required) {
        UserRole[] roles = required.value();
        return roles.length == 1 && roles[0] == UserRole.ADMIN;
    }
}
