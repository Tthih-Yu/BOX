package com.example.materialpull.common;

import com.example.materialpull.enums.UserRole;
import java.util.Arrays;
import java.util.List;

/**
 * 请求上下文
 * 使用 ThreadLocal 存储当前请求的用户信息和追踪ID
 */
public final class RequestContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> REAL_NAME = new ThreadLocal<>();
    private static final ThreadLocal<UserRole> ROLE = new ThreadLocal<>();
    
    // DataScope 新增字段
    private static final ThreadLocal<String> FACTORY = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> DELIVERY_AREAS = new ThreadLocal<>();
    /** 仅由认证过滤器在 /scan/* 请求中标记。 */
    private static final ThreadLocal<Boolean> TRUSTED_SCANNER = new ThreadLocal<>();

    private RequestContext() {}

    public static void setTraceId(String traceId) { TRACE_ID.set(traceId); }
    public static String getTraceId() { return TRACE_ID.get(); }

    /**
     * 设置登录用户（原有方法，保持兼容）
     */
    public static void setLoginUser(Long userId, String username, String realName, UserRole role) {
        USER_ID.set(userId);
        USERNAME.set(username);
        REAL_NAME.set(realName);
        ROLE.set(role);
        // 注意：factory 和 deliveryAreas 需要在 AuthService 中单独设置
    }
    
    /**
     * 设置登录用户（DataScope 扩展版本）
     */
    public static void setLoginUser(Long userId, String username, String realName, UserRole role, 
                                     String factory, List<String> deliveryAreas) {
        USER_ID.set(userId);
        USERNAME.set(username);
        REAL_NAME.set(realName);
        ROLE.set(role);
        FACTORY.set(factory);
        DELIVERY_AREAS.set(deliveryAreas);
    }

    public static Long getUserId() { return USER_ID.get(); }
    public static String getUsername() { return USERNAME.get(); }
    public static String getRealName() { return REAL_NAME.get(); }
    public static UserRole getRole() { return ROLE.get(); }
    public static boolean isAdmin() { return ROLE.get() == UserRole.ADMIN; }
    
    // DataScope 新增方法
    public static String getFactory() { return FACTORY.get(); }
    public static List<String> getDeliveryAreas() { return DELIVERY_AREAS.get(); }
    public static void setTrustedScanner(boolean trusted) { TRUSTED_SCANNER.set(trusted); }
    public static boolean isTrustedScanner() { return Boolean.TRUE.equals(TRUSTED_SCANNER.get()); }
    
    /**
     * 判断是否为全局管理员（ADMIN 且 factory 为 null）
     */
    public static boolean isGlobalAdmin() {
        return ROLE.get() == UserRole.ADMIN && FACTORY.get() == null;
    }

    public static void requireAnyRole(UserRole... roles) {
        UserRole current = ROLE.get();
        if (current == UserRole.ADMIN) return;
        if (roles != null && Arrays.stream(roles).anyMatch(r -> r == current)) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号无权执行该操作");
    }

    public static void clear() {
        TRACE_ID.remove();
        USER_ID.remove();
        USERNAME.remove();
        REAL_NAME.remove();
        ROLE.remove();
        FACTORY.remove();
        DELIVERY_AREAS.remove();
        TRUSTED_SCANNER.remove();
    }
}
