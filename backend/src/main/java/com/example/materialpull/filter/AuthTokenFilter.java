package com.example.materialpull.filter;

import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.IpMatcher;
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
        if (path.equals("/auth/login") || path.equals("/device/login") || path.equals("/health/ready")) return true;
        // 健康探针始终放行（容器/K8s liveness/readiness 依赖）；show-details=when_authorized 不泄露细节。
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) return true;
        // 其余 /actuator/**（prometheus/metrics/info 等）不在此放行，交由 doFilterInternal 做应用层防护。
        if (securityProperties.isDevToolsEnabled() && (path.startsWith("/h2-console") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs"))) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path != null && path.startsWith("/scan/") && !deviceAccessAllowed(request)) {
            writeForbidden(response);
            return;
        }
        // 敏感 Actuator 端点(prometheus/metrics/info 等)应用层防护：令牌或真实来源 IP 白名单二选一。
        if (path != null && path.startsWith("/actuator")) {
            if (actuatorAccessAllowed(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            writeForbidden(response);
            return;
        }
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
        bindRequestUser(request, user.userId(), user.username(), user.realName(), user.role(), user.factory(), user.deliveryAreas());
        filterChain.doFilter(request, response);
    }

    /**
     * /scan/* 免登录访问的加固校验：IP 白名单 + 可选设备密钥。
     * 携带合法 X-Api-Key 的外部系统调用走更严格的外部认证分支，此处豁免。
     */
    private boolean deviceAccessAllowed(HttpServletRequest request) {
        String configuredApiKey = securityProperties.getExternalApiKey();
        String suppliedApiKey = request.getHeader("X-Api-Key");
        boolean externalSystemCall = configuredApiKey != null && !configuredApiKey.isBlank()
                && !"CHANGE_ME_EXTERNAL_API_KEY".equals(configuredApiKey)
                && Objects.equals(configuredApiKey, suppliedApiKey);
        if (externalSystemCall) return true;

        SecurityProperties.DeviceAuth deviceAuth = securityProperties.getDeviceAuth();

        if (!IpMatcher.matchesAny(clientIp(request), deviceAuth.getAllowedIpCidrs())) {
            return false;
        }
        if (deviceAuth.isRequireDeviceKey()) {
            String deviceKey = trimToNull(request.getHeader("X-Device-Key"));
            if (deviceKey == null || deviceAuth.getDeviceKeys() == null
                    || deviceAuth.getDeviceKeys().stream().noneMatch(k -> Objects.equals(k, deviceKey))) {
                return false;
            }
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 敏感 Actuator 端点访问控制：
     *   1) 若配置了管理令牌，请求携带匹配的 X-Actuator-Token 或 Bearer 令牌即放行；
     *   2) 否则按真实来源 IP(remoteAddr，不信任可伪造的 X-Forwarded-For)命中白名单放行。
     * 默认白名单含回环与内网私有网段，保证 nginx 反代、内网 Prometheus 抓取正常；公网直连被拒。
     */
    private boolean actuatorAccessAllowed(HttpServletRequest request) {
        SecurityProperties.Actuator actuator = securityProperties.getActuator();
        String token = actuator == null ? null : actuator.getToken();
        if (token != null && !token.isBlank()) {
            String supplied = trimToNull(request.getHeader("X-Actuator-Token"));
            if (supplied == null) {
                String auth = request.getHeader("Authorization");
                if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) supplied = auth.substring(7).trim();
            }
            if (Objects.equals(token.trim(), supplied)) return true;
        }
        java.util.List<String> cidrs = actuator == null ? null : actuator.getAllowedIpCidrs();
        // 未配置任何白名单时按真实来源判断是否为内网/回环，避免误配导致完全放开或完全锁死。
        if (cidrs == null || cidrs.isEmpty()) return isLoopbackOrPrivate(request.getRemoteAddr());
        return IpMatcher.matchesAny(request.getRemoteAddr(), cidrs);
    }

    private boolean isLoopbackOrPrivate(String ip) {
        return IpMatcher.matchesAny(ip, java.util.List.of(
                "127.0.0.1/32", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"));
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
        RequestContext.setTrustedScanner(true);
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
        if (path.startsWith("/scan/")) RequestContext.setTrustedScanner(true);
        return true;
    }

    private boolean isExternalEndpoint(String path) {
        return path != null && (path.startsWith("/scan/")
                || path.startsWith("/integrations/sap/")
                || path.startsWith("/integrations/ims/")
                || path.startsWith("/integrations/ppc/")
                || path.equals("/print-jobs/callback")
                || path.equals("/print-jobs/next")
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
        bindRequestUser(request, user.userId(), user.username(), user.realName(), user.role(), user.factory(), user.deliveryAreas());
        return true;
    }

    private void bindRequestUser(HttpServletRequest request, Long userId, String username, String realName, UserRole role, String factory, java.util.List<String> deliveryAreas) {
        // DataScope: 使用新的 6 参数方法设置范围
        RequestContext.setLoginUser(userId, username, realName, role, factory, deliveryAreas);
        request.setAttribute("loginUserId", userId);
        request.setAttribute("loginUser", username);
        request.setAttribute("loginRealName", realName);
        request.setAttribute("loginRole", role == null ? null : role.name());
        request.setAttribute("loginFactory", factory);
        request.setAttribute("loginDeliveryAreas", deliveryAreas == null ? java.util.List.of() : java.util.List.copyOf(deliveryAreas));
    }
    
    private void bindRequestUser(HttpServletRequest request, Long userId, String username, String realName, UserRole role) {
        // 向后兼容：设备和外部系统调用暂时不设置范围
        bindRequestUser(request, userId, username, realName, role, null, null);
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

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        String requestId = RequestContext.getTraceId();
        String body = "{"
                + "\"success\":false,"
                + "\"code\":\"" + ErrorCode.FORBIDDEN.code + "\","
                + "\"message\":\"设备未授权访问，请检查设备密钥或来源 IP 白名单\","
                + "\"requestId\":\"" + (requestId == null ? "" : requestId) + "\","
                + "\"timestamp\":\"" + LocalDateTime.now() + "\""
                + "}";
        response.getWriter().write(body);
    }
}
