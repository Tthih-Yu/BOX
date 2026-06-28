package com.example.materialpull.filter;

import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.common.SecurityProperties;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.service.AuthTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

@Component
@Order(2)
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {
    private final AuthTokenService tokenService;
    private final SecurityProperties securityProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (path == null) return true;
        if (path.equals("/auth/login") || path.equals("/health/ready")) return true;
        if (securityProperties.isDevToolsEnabled() && (path.startsWith("/h2-console") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs"))) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (authenticateDeviceTerminal(request, path)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (authenticateExternalSystem(request, path)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (authenticateWebsocketTicket(request, path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = readBearerToken(request);
        var session = tokenService.validate(token);
        if (session.isEmpty() && securityProperties.isAllowWebsocketQueryToken() && "/ws".equals(path)) {
            session = tokenService.validate(request.getParameter("token"));
        }
        if (session.isEmpty()) {
            writeUnauthorized(response);
            return;
        }
        AuthTokenService.SessionUser user = session.get();
        bindRequestUser(request, user.userId(), user.username(), user.realName(), user.role());
        filterChain.doFilter(request, response);
    }

    /**
     * 安卓终端 /scan/* 不再使用任何账号 / 密码 / Token：
     * 只要请求里带上员工工号（显示用）和设备号即可放行；员工工号不参与任何鉴权或业务校验。
     * 如果客户端仍按外部系统约定带了正确的 X-Api-Key，则继续走外部系统认证分支。
     */
    private boolean authenticateDeviceTerminal(HttpServletRequest request, String path) {
        if (path == null || !path.startsWith("/scan/")) return false;
        // 若调用方带了与系统配置一致的 X-Api-Key，让它走更严格的外部系统认证，方便审计区分
        String configured = securityProperties.getExternalApiKey();
        String supplied = request.getHeader("X-Api-Key");
        if (configured != null && !configured.isBlank() && !"CHANGE_ME_EXTERNAL_API_KEY".equals(configured) && Objects.equals(configured, supplied)) {
            return false;
        }
        String employeeNo = trimToNull(request.getHeader("X-Employee-No"));
        String deviceNo = trimToNull(request.getHeader("X-Device-No"));
        String username = employeeNo != null ? ("device-" + employeeNo) : (deviceNo != null ? ("device-" + deviceNo) : "android-device");
        String realName = employeeNo != null ? ("工号 " + employeeNo) : "现场设备";
        bindRequestUser(request, null, username, realName, UserRole.LINE);
        return true;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean authenticateExternalSystem(HttpServletRequest request, String path) {
        if (!isExternalEndpoint(path)) return false;
        String configured = securityProperties.getExternalApiKey();
        if (configured == null || configured.isBlank() || "CHANGE_ME_EXTERNAL_API_KEY".equals(configured)) return false;
        String supplied = request.getHeader("X-Api-Key");
        if (!Objects.equals(configured, supplied)) return false;
        bindRequestUser(request, null, "external-system", "外部系统", UserRole.SYSTEM);
        return true;
    }

    private boolean isExternalEndpoint(String path) {
        return path != null && (path.startsWith("/scan/")
                || path.startsWith("/integrations/sap/")
                || path.startsWith("/integrations/ims/")
                || path.startsWith("/integrations/ppc/")
                || path.equals("/print-jobs/callback")
                || path.equals("/agv-jobs/callback")
                || path.matches("^/agv-jobs/[^/]+/callback$"));
    }

    private boolean authenticateWebsocketTicket(HttpServletRequest request, String path) {
        if (!"/ws".equals(path)) return false;
        String ticket = request.getParameter("ticket");
        if (ticket == null || ticket.isBlank()) return false;
        var session = tokenService.consumeWebsocketTicket(ticket);
        if (session.isEmpty()) return false;
        AuthTokenService.SessionUser user = session.get();
        bindRequestUser(request, user.userId(), user.username(), user.realName(), user.role());
        return true;
    }

    private void bindRequestUser(HttpServletRequest request, Long userId, String username, String realName, UserRole role) {
        RequestContext.setLoginUser(userId, username, realName, role);
        request.setAttribute("loginUserId", userId);
        request.setAttribute("loginUser", username);
        request.setAttribute("loginRealName", realName);
        request.setAttribute("loginRole", role == null ? null : role.name());
    }

    private String readBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) return auth.substring(7).trim();
        String token = request.getHeader("X-Auth-Token");
        return token == null ? null : token.trim();
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        String requestId = RequestContext.getTraceId();
        String body = "{"
                + "\"success\":false,"
                + "\"code\":\"" + ErrorCode.UNAUTHORIZED.code + "\","
                + "\"message\":\"未登录或登录已过期，请重新登录\","
                + "\"requestId\":\"" + (requestId == null ? "" : requestId) + "\","
                + "\"timestamp\":\"" + LocalDateTime.now() + "\""
                + "}";
        response.getWriter().write(body);
    }
}
