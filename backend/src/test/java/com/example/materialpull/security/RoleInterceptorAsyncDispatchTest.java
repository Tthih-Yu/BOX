package com.example.materialpull.security;

import com.example.materialpull.common.RequestContext;
import com.example.materialpull.common.SecurityProperties;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.filter.AuthTokenFilter;
import com.example.materialpull.filter.TraceIdFilter;
import com.example.materialpull.service.AuthTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.method.HandlerMethod;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

class RoleInterceptorAsyncDispatchTest {
    private final AuthTokenService tokenService = Mockito.mock(AuthTokenService.class);
    private final AuthTokenFilter authFilter = new AuthTokenFilter(tokenService, new SecurityProperties());
    private final RoleInterceptor interceptor = new RoleInterceptor();
    private final StreamingController controller = new StreamingController();

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void restoresAuthenticatedUserForStreamingAsyncRedispatchAndCleansItAfterward() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        AuthTokenService.SessionUser session = new AuthTokenService.SessionUser(
                "valid-token", 7L, "tester", "测试员", UserRole.WAREHOUSE,
                now, now.minusDays(1), now.plusHours(1), "弋江", List.of("T26 Floor"));
        when(tokenService.validate("valid-token")).thenReturn(Optional.of(session));

        MockHttpServletRequest initialRequest = new MockHttpServletRequest("GET", "/export");
        initialRequest.setServletPath("/export");
        initialRequest.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse initialResponse = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(controller, StreamingController.class.getDeclaredMethod("export"));

        authFilter.doFilter(initialRequest, initialResponse, (request, response) ->
                assertDoesNotThrow(() -> interceptor.preHandle((MockHttpServletRequest) request,
                        (MockHttpServletResponse) response, handler)));
        RequestContext.clear(); // matches TraceIdFilter cleanup after the initial dispatcher returns

        initialRequest.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
        assertDoesNotThrow(() -> interceptor.preHandle(initialRequest, initialResponse, handler));
        assertNull(RequestContext.getTraceId());
        interceptor.afterCompletion(initialRequest, initialResponse, handler, null);
        assertNull(RequestContext.getRole());
    }

    @RestController
    @RequireRoles(UserRole.WAREHOUSE)
    private static class StreamingController {
        @GetMapping("/export")
        public ResponseEntity<DeferredResult<Void>> export() {
            return ResponseEntity.ok(new DeferredResult<>());
        }
    }
}
