package com.example.materialpull.filter;

import com.example.materialpull.common.RequestContext;
import com.example.materialpull.enums.UserRole;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdFilterTest {
    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void clearsAllThreadLocalsAfterNormalRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mappings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            RequestContext.setLoginUser(7L, "planner", "计划员", UserRole.PLANNER,
                    "弋江", List.of("T26 Floor"));
            RequestContext.setTrustedScanner(true);
            assertNotNull(RequestContext.getTraceId());
        });

        assertContextCleared();
    }

    @Test
    void clearsAllThreadLocalsAfterExceptionalRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mappings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, (req, res) -> {
            RequestContext.setLoginUser(7L, "planner", "计划员", UserRole.PLANNER,
                    "弋江", List.of("T26 Floor"));
            RequestContext.setTrustedScanner(true);
            throw new ServletException("boom");
        }));

        assertContextCleared();
    }

    private void assertContextCleared() {
        assertNull(RequestContext.getTraceId());
        assertNull(RequestContext.getUserId());
        assertNull(RequestContext.getUsername());
        assertNull(RequestContext.getRole());
        assertNull(RequestContext.getFactory());
        assertNull(RequestContext.getDeliveryAreas());
        assertFalse(RequestContext.isTrustedScanner());
    }
}
