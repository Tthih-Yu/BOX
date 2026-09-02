package com.example.materialpull.controller;

import com.example.materialpull.common.GlobalExceptionHandler;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.common.SecurityProperties;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.filter.AuthTokenFilter;
import com.example.materialpull.repository.*;
import com.example.materialpull.security.RoleInterceptor;
import com.example.materialpull.service.AuthTokenService;
import com.example.materialpull.service.BasicDataService;
import com.example.materialpull.service.LogExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MappingAuthChainIntegrationTest {
    @Mock MaterialRepository materialRepository;
    @Mock MaterialMappingRepository mappingRepository;
    @Mock StationMaterialRepository stationMaterialRepository;
    @Mock BoxRepository boxRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock SystemConfigRepository configRepository;
    @Mock BasicDataService service;
    @Mock LogExportService logExportService;
    @Mock com.example.materialpull.service.DataScopeService dataScopeService;
    @Mock AuthTokenService tokenService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        BasicDataController controller = new BasicDataController(materialRepository, mappingRepository,
                stationMaterialRepository, boxRepository, inventoryRepository, configRepository,
                service, logExportService, dataScopeService);
        AuthTokenFilter filter = new AuthTokenFilter(tokenService, new SecurityProperties());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(filter)
                .addInterceptors(new RoleInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void missingTokenIsRejectedByRealAuthFilter() throws Exception {
        when(tokenService.validate(null)).thenReturn(Optional.empty());

        mvc.perform(get("/mappings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0401"));

        verifyNoInteractions(service);
    }

    @Test
    void tokenScopeIsBoundBeforeScopedServiceCall() throws Exception {
        AuthTokenService.SessionUser session = session(UserRole.PLANNER, "弋江", List.of("T26 Floor"));
        when(tokenService.validate("valid-token")).thenReturn(Optional.of(session));
        when(service.mappings()).thenAnswer(invocation -> {
            assertEquals("弋江", RequestContext.getFactory());
            assertEquals(List.of("T26 Floor"), RequestContext.getDeliveryAreas());
            return List.of();
        });

        mvc.perform(get("/mappings").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());

        verify(service).mappings();
    }

    @Test
    void disallowedRoleIsRejectedByRealRoleInterceptor() throws Exception {
        when(tokenService.validate("line-token"))
                .thenReturn(Optional.of(session(UserRole.LINE, "弋江", List.of("T26 Floor"))));

        mvc.perform(get("/mappings").header("Authorization", "Bearer line-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A0403"));

        verifyNoInteractions(service);
    }

    private AuthTokenService.SessionUser session(UserRole role, String factory, List<String> areas) {
        LocalDateTime now = LocalDateTime.now();
        return new AuthTokenService.SessionUser("token", 7L, "tester", "测试员", role,
                now, now.minusDays(1), now.plusHours(1), factory, areas);
    }
}
