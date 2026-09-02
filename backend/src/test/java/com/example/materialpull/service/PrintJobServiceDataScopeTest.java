package com.example.materialpull.service;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.PrintJobEntity;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.enums.PrintJobStatus;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.PrintJobRepository;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import com.example.materialpull.repository.SystemConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintJobServiceDataScopeTest {
    @Mock PrintJobRepository printJobRepository;
    @Mock ReplenishmentTaskRepository taskRepository;
    @Mock AuditService auditService;
    @Mock RealtimePushService pushService;
    @Mock AppProperties properties;
    @Mock ExternalHttpClient externalHttpClient;
    @Mock SystemConfigRepository systemConfigRepository;
    @Spy DataScopeService dataScopeService = new DataScopeService();
    @InjectMocks PrintJobService service;

    @BeforeEach
    void login() {
        RequestContext.setLoginUser(7L, "warehouse", "仓库", UserRole.WAREHOUSE,
                "弋江", List.of("T26 Floor", "T26 Rear"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void listUsesFactoryAndDeliveryAreas() {
        when(printJobRepository.findScopedIncludingTaskFallback(eq("弋江"), eq(List.of("T26 Floor", "T26 Rear")),
                isNull(), eq(""), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(new PrintJobEntity())));

        assertEquals(1, service.list(null).size());

        verify(printJobRepository).findScopedIncludingTaskFallback(eq("弋江"), eq(List.of("T26 Floor", "T26 Rear")),
                isNull(), eq(""), any(Pageable.class));
        verify(printJobRepository, never()).findTop1000ByOrderByCreatedAtDesc();
    }

    @Test
    void pageUsesTheSameScopeWithFilters() {
        when(printJobRepository.findScopedIncludingTaskFallback(eq("弋江"), eq(List.of("T26 Floor", "T26 Rear")),
                eq(PrintJobStatus.PRINTED), eq("BROWSER"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.page("PRINTED", "browser", 0, 20);

        verify(printJobRepository).findScopedIncludingTaskFallback(eq("弋江"), eq(List.of("T26 Floor", "T26 Rear")),
                eq(PrintJobStatus.PRINTED), eq("BROWSER"), any(Pageable.class));
    }

    @Test
    void createRejectsTaskOutsideCurrentAreaBeforeSaving() {
        FactoryDtos.PrintRequest request = new FactoryDtos.PrintRequest();
        request.taskNo = "TASK-OUTSIDE";
        ReplenishmentTaskEntity task = task("TASK-OUTSIDE", "弋江", "T26 Engine");
        when(taskRepository.findByTaskNo("TASK-OUTSIDE")).thenReturn(Optional.of(task));

        assertThrows(BusinessException.class, () -> service.createForTask(request));

        verify(printJobRepository, never()).save(any());
    }

    @Test
    void newJobSnapshotsFactoryAndDeliveryAreaFromTask() {
        ReplenishmentTaskEntity task = task("TASK-01", "弋江", "T26 Floor");
        when(properties.getDefaultPrintType()).thenReturn("WAREHOUSE_BARCODE_LABEL");
        when(properties.getDefaultPrinterName()).thenReturn("Printer-01");
        when(properties.isPrintPullMode()).thenReturn(true);
        when(printJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PrintJobEntity job = service.createForTask(task, "tester");

        assertEquals("弋江", job.getFactory());
        assertEquals("T26 Floor", job.getDeliveryArea());
    }

    private ReplenishmentTaskEntity task(String taskNo, String factory, String area) {
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setTaskNo(taskNo);
        task.setFactory(factory);
        task.setDeliveryArea(area);
        return task;
    }
}
