package com.example.materialpull.filter;

import com.example.materialpull.common.RequestContext;
import com.example.materialpull.common.SecurityProperties;
import com.example.materialpull.service.AuthTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuthTokenFilterDeviceCompatibilityTest {
    private final SecurityProperties properties = new SecurityProperties();
    private final AuthTokenFilter filter = new AuthTokenFilter(mock(AuthTokenService.class), properties);

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void deviceRegistrationIsPublicAsControllerContractRequires() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/device/login");
        request.setServletPath("/device/login");

        assertTrue(filter.shouldNotFilter(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/logout", "/auth/ws-ticket", "/device/login/extra",
            "/health/factory", "/tasks", "/mappings"})
    void similarOrBusinessPathsAreNotAccidentallyPublic(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void legacyScanRequestGetsTrustedScannerIdentityWithoutAccountScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/scan/empty");
        request.setServletPath("/scan/empty");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Employee-No", "10001");
        request.addHeader("X-Device-No", "ANDROID-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("device-10001", RequestContext.getUsername());
        assertNull(RequestContext.getFactory());
        assertTrue(RequestContext.isTrustedScanner());
    }
}
