package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataScopeServiceTest {
    private final DataScopeService service = new DataScopeService();

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void factoryAreaAllowsOnlyCurrentFactoryAndAuthorizedArea() {
        RequestContext.setLoginUser(1L, "warehouse", "仓库", UserRole.WAREHOUSE,
                "弋江", List.of("T26 Floor", "EOVA IP"));

        assertTrue(service.canAccessFactoryArea(" 弋江 ", "t26 floor"));
        assertFalse(service.canAccessFactoryArea("三山", "T26 Floor"));
        assertFalse(service.canAccessFactoryArea("弋江", "未授权区域"));
    }

    @Test
    void missingUserScopeFailsClosed() {
        RequestContext.setLoginUser(2L, "viewer", "查看", UserRole.VIEWER,
                null, List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireAccessFactoryArea("弋江", "T26 Floor"));
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void unassignedBusinessDataIsHiddenFromScopedUser() {
        RequestContext.setLoginUser(3L, "line", "产线", UserRole.LINE,
                "弋江", List.of("T26 Floor"));

        assertFalse(service.canAccessFactoryArea(null, "T26 Floor"));
        assertFalse(service.canAccessFactoryArea("弋江", null));
        assertFalse(service.canAccessUnassigned());
    }

    @Test
    void globalAdminCanAccessAssignedAndUnassignedData() {
        RequestContext.setLoginUser(4L, "admin", "管理员", UserRole.ADMIN,
                null, List.of());

        assertTrue(service.isGlobalAdmin());
        assertTrue(service.canAccessFactoryArea("弋江", "T26 Floor"));
        assertTrue(service.canAccessFactoryArea(null, null));
        assertTrue(service.canAccessUnassigned());
        assertDoesNotThrow(service::requireGlobalAdmin);
    }

    @Test
    void factoryBoundAdminCannotUseGlobalMaintenanceEntry() {
        RequestContext.setLoginUser(5L, "factory-admin", "工厂管理员", UserRole.ADMIN,
                "弋江", List.of("T26 Floor"));

        BusinessException exception = assertThrows(BusinessException.class, service::requireGlobalAdmin);
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void malformedAdminWithAreasCannotBecomeGlobal() {
        RequestContext.setLoginUser(6L, "broken-admin", "异常管理员", UserRole.ADMIN,
                null, List.of("T26 Floor"));

        assertFalse(service.isGlobalAdmin());
        assertThrows(BusinessException.class, service::requireGlobalAdmin);
    }

    @Test
    void roleFactoryAreaMatrixFailsClosed() {
        for (UserRole role : List.of(UserRole.SUB_ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE,
                UserRole.LINE, UserRole.VIEWER)) {
            RequestContext.setLoginUser(10L, role.name(), role.name(), role,
                    "弋江", List.of("T26 Floor", "T26 Rear"));
            assertTrue(service.canAccessFactoryArea("弋江", "T26 Floor"), role.name());
            assertTrue(service.canAccessFactoryOnly("弋江"), role.name());
            assertFalse(service.canAccessFactoryArea("弋江", "未授权"), role.name());
            assertFalse(service.canAccessFactoryArea("三山", "T26 Floor"), role.name());
            assertFalse(service.canAccessFactoryArea(null, "T26 Floor"), role.name());
            assertFalse(service.canAccessFactoryArea("弋江", null), role.name());
            RequestContext.clear();
        }
    }

    @Test
    void batchScopeRejectsMixedFactoryAndNullItems() {
        record Scoped(String factory, String area) {}
        RequestContext.setLoginUser(11L, "warehouse", "仓库", UserRole.WAREHOUSE,
                "弋江", List.of("T26 Floor"));

        assertThrows(BusinessException.class, () -> service.requireAccessBatch(
                List.of(new Scoped("弋江", "T26 Floor"), new Scoped("三山", "T26 Floor")),
                Scoped::factory, Scoped::area));
        assertThrows(BusinessException.class, () -> service.requireAccessBatch(
                List.of(new Scoped("弋江", "T26 Floor"), new Scoped("弋江", null)),
                Scoped::factory, Scoped::area));
    }

    @Test
    void nullOrBlankFactoryAreaIsUnassigned() {
        assertTrue(service.isUnassigned(null, "T26 Floor"));
        assertTrue(service.isUnassigned("弋江", null));
        assertTrue(service.isUnassigned("弋江", "  "));
        assertFalse(service.isUnassigned("弋江", "T26 Floor"));
    }

    @Test
    void trustedScannerAcceptsOnlyCompleteServerResolvedScope() {
        RequestContext.setLoginUser(null, "device-01", "现场设备", UserRole.LINE, null, null);
        RequestContext.setTrustedScanner(true);

        assertTrue(service.canAccessFactoryArea("弋江", "T26 Floor"));
        assertFalse(service.canAccessFactoryArea("弋江", null));
        assertFalse(service.canAccessFactoryArea(null, "T26 Floor"));
        assertThrows(BusinessException.class, () -> service.canAccessFactoryOnly("弋江"));
    }
}
