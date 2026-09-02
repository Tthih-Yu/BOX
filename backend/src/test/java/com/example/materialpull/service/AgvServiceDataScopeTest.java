package com.example.materialpull.service;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.AgvJobEntity;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.AgvJobRepository;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgvServiceDataScopeTest {
    @Mock AgvJobRepository agvJobRepository;
    @Mock ReplenishmentTaskRepository taskRepository;
    @Mock AuditService auditService;
    @Mock RealtimePushService pushService;
    @Mock AppProperties properties;
    @Mock ExternalHttpClient externalHttpClient;
    @Spy DataScopeService dataScopeService = new DataScopeService();
    @InjectMocks AgvService service;

    @BeforeEach
    void login() {
        RequestContext.setLoginUser(7L, "warehouse", "仓库", UserRole.WAREHOUSE, "弋江", List.of("T26 Floor"));
    }

    @AfterEach
    void clear() { RequestContext.clear(); }

    @Test
    void listFiltersJobsThroughRelatedTaskScope() {
        AgvJobEntity allowed = job("AGV-1", "TASK-1");
        AgvJobEntity denied = job("AGV-2", "TASK-2");
        when(agvJobRepository.findTop1000ByOrderByCreatedAtDesc()).thenReturn(List.of(allowed, denied));
        when(taskRepository.findByTaskNoIn(List.of("TASK-1", "TASK-2"))).thenReturn(List.of(
                task("TASK-1", "弋江", "T26 Floor"), task("TASK-2", "三山", "T26 Floor")));

        List<AgvJobEntity> result = service.list(null);

        assertEquals(List.of(allowed), result);
    }

    @Test
    void manualDispatchRejectsTaskOutsideScope() {
        FactoryDtos.AgvDispatchRequest req = new FactoryDtos.AgvDispatchRequest();
        req.taskNo = "TASK-2";
        when(taskRepository.findByTaskNo("TASK-2")).thenReturn(Optional.of(task("TASK-2", "三山", "T26 Floor")));

        assertThrows(BusinessException.class, () -> service.dispatch(req));
        verify(agvJobRepository, never()).save(any());
    }

    @Test
    void unscopedSystemIdentityCannotListAllAgvJobs() {
        RequestContext.setLoginUser(null, "external-system", "外部系统", UserRole.SYSTEM, null, List.of());
        when(agvJobRepository.findTop1000ByOrderByCreatedAtDesc()).thenReturn(List.of(job("AGV-1", "TASK-1")));

        assertThrows(BusinessException.class, () -> service.list(null));
        verify(taskRepository, never()).findByTaskNoIn(any());
    }

    private AgvJobEntity job(String no, String taskNo) {
        AgvJobEntity job = new AgvJobEntity(); job.setAgvJobNo(no); job.setTaskNo(taskNo); return job;
    }

    private ReplenishmentTaskEntity task(String no, String factory, String area) {
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity(); task.setTaskNo(no); task.setFactory(factory); task.setDeliveryArea(area); return task;
    }
}
