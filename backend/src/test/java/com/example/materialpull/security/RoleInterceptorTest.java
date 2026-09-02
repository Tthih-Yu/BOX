package com.example.materialpull.security;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleInterceptorTest {
    private final RoleInterceptor interceptor = new RoleInterceptor();
    private final GuardedController controller = new GuardedController();

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void systemCannotBypassHumanRoleEndpoint() throws Exception {
        loginAs(UserRole.SYSTEM);

        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(null, null, handler("warehouseOnly")));
    }

    @Test
    void systemCanAccessExplicitSystemEndpoint() throws Exception {
        loginAs(UserRole.SYSTEM);

        assertTrue(interceptor.preHandle(null, null, handler("systemOnly")));
    }

    @Test
    void adminRemainsHighestPrivilegeRole() throws Exception {
        loginAs(UserRole.ADMIN);

        assertTrue(interceptor.preHandle(null, null, handler("warehouseOnly")));
    }

    @Test
    void missingIdentityIsRejectedOnGuardedEndpoint() throws Exception {
        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(null, null, handler("systemOnly")));
    }

    @Test
    void endpointWithoutRoleAnnotationRemainsAuthenticatedByFilterPolicy() throws Exception {
        loginAs(UserRole.VIEWER);

        assertDoesNotThrow(() -> interceptor.preHandle(null, null, handler("authenticatedOnly")));
    }

    private void loginAs(UserRole role) {
        RequestContext.setLoginUser(1L, "test", "Test", role, null, java.util.List.of());
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(controller, GuardedController.class.getDeclaredMethod(methodName));
    }

    private static class GuardedController {
        @RequireRoles(UserRole.WAREHOUSE)
        public void warehouseOnly() {}

        @RequireRoles(UserRole.SYSTEM)
        public void systemOnly() {}

        public void authenticatedOnly() {}
    }
}
