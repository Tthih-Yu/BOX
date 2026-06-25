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
