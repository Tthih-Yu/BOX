package com.example.materialpull.service;

import com.example.materialpull.common.RequestContext;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialUsageDashboardDataScopeTest {
    @Mock ReplenishmentTaskRepository taskRepository;
    @Mock MaterialMappingRepository mappingRepository;
    @Spy DataScopeService dataScopeService = new DataScopeService();
    @InjectMocks MaterialUsageDashboardService service;

    @BeforeEach
    void login() {
        RequestContext.setLoginUser(9L, "warehouse", "仓库", UserRole.WAREHOUSE, "弋江", List.of("T26 Floor"));
    }

    @AfterEach
    void clear() { RequestContext.clear(); }

    @Test
    void dashboardScopesTasksAndMappings() {
        service.dashboard("2026-08-01", "2026-08-31");

        verify(taskRepository).findByFactoryIgnoreCaseAndDeliveryAreaInAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq("弋江"), eq(List.of("T26 Floor")), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(mappingRepository).findByFactoryIgnoreCaseAndDeliveryAreaInOrderByLineMaterialCodeAscMappingOrderAscIdAsc(
                "弋江", List.of("T26 Floor"));
        verify(taskRepository, never()).findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any());
    }
}
