package com.example.materialpull.security;

import com.example.materialpull.enums.UserRole;
import com.example.materialpull.service.AuthTokenService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionUserDataScopeTest {

    @Test
    void conversionPreservesAssignedFactoryAndAreas() {
        SessionUser user = SessionUser.fromTokenSession(session(UserRole.WAREHOUSE, "弋江", List.of("T26 Floor", "T18FL4 Floor")));

        assertEquals("弋江", user.getFactory());
        assertEquals(List.of("T26 Floor", "T18FL4 Floor"), user.getDeliveryAreas());
        assertFalse(user.isGlobalAdmin());
    }

    @Test
    void adminWithAssignedAreaIsNotGlobal() {
        SessionUser user = SessionUser.fromTokenSession(session(UserRole.ADMIN, null, List.of("T26 Floor")));

        assertFalse(user.isGlobalAdmin());
    }

    @Test
    void adminWithoutFactoryOrAreasRemainsGlobal() {
        SessionUser user = SessionUser.fromTokenSession(session(UserRole.ADMIN, null, null));

        assertTrue(user.isGlobalAdmin());
    }

    private AuthTokenService.SessionUser session(UserRole role, String factory, List<String> areas) {
        LocalDateTime now = LocalDateTime.now();
        return new AuthTokenService.SessionUser("test-token", 7L, "tester", "测试员", role,
                now, null, now.plusHours(1), factory, areas);
    }
}
