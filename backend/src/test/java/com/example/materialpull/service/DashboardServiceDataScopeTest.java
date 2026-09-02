package com.example.materialpull.service;

import com.example.materialpull.common.RequestContext;
import com.example.materialpull.enums.TaskStatus;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceDataScopeTest {
    @Mock MaterialRepository materialRepository;
    @Mock StationMaterialRepository stationMaterialRepository;
    @Mock BoxRepository boxRepository;
    @Mock LabelRepository labelRepository;
    @Mock ReplenishmentTaskRepository taskRepository;
    @Mock ScanLogRepository scanLogRepository;
    @Mock InventoryRepository inventoryRepository;
    @Spy DataScopeService dataScopeService = new DataScopeService();
    @InjectMocks DashboardService service;

    @BeforeEach
    void login() {
        RequestContext.setLoginUser(8L, "planner", "计划员", UserRole.PLANNER, "弋江", List.of("T26 Floor"));
    }

    @AfterEach
    void clear() { RequestContext.clear(); }

    @Test
    void dashboardUsesScopedQueriesIncludingScanHistory() {
        when(inventoryRepository.findLowStockScoped("弋江", List.of("T26 Floor"))).thenReturn(List.of());
        when(taskRepository.findByFactoryIgnoreCaseAndDeliveryAreaInAndStatusIn(eq("弋江"), eq(List.of("T26 Floor")), anyList()))
                .thenReturn(List.of());

        var result = service.dashboard("today");

        verify(boxRepository).countByFactoryIgnoreCaseAndDeliveryAreaIn("弋江", List.of("T26 Floor"));
        verify(labelRepository).countByFactoryIgnoreCaseAndDeliveryAreaIn("弋江", List.of("T26 Floor"));
        verify(taskRepository, never()).findAll();
        verify(scanLogRepository).findTop1000ByFactoryIgnoreCaseAndDeliveryAreaInOrderByScanAtDesc(
                "弋江", List.of("T26 Floor"));
        verify(scanLogRepository, never()).findTop1000ByOrderByScanAtDesc();
        assertTrue(result.latestScans.isEmpty());
    }
}
