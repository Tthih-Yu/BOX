package com.example.materialpull.common;

import com.example.materialpull.enums.UserRole;
import java.util.Arrays;

public final class RequestContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> REAL_NAME = new ThreadLocal<>();
    private static final ThreadLocal<UserRole> ROLE = new ThreadLocal<>();

    private RequestContext() {}

    public static void setTraceId(String traceId) { TRACE_ID.set(traceId); }
    public static String getTraceId() { return TRACE_ID.get(); }

    public static void setLoginUser(Long userId, String username, String realName, UserRole role) {
        USER_ID.set(userId);
        USERNAME.set(username);
        REAL_NAME.set(realName);
        ROLE.set(role);
    }

    public static Long getUserId() { return USER_ID.get(); }
    public static String getUsername() { return USERNAME.get(); }
    public static String getRealName() { return REAL_NAME.get(); }
    public static UserRole getRole() { return ROLE.get(); }
    public static boolean isAdmin() { return ROLE.get() == UserRole.ADMIN; }

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
    }
}
