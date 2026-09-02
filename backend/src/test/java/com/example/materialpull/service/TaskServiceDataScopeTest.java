package com.example.materialpull.service;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.TaskActionRequest;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.enums.TaskStatus;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.BoxRepository;
import com.example.materialpull.repository.InventoryRepository;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import com.example.materialpull.resilience.BusinessLockService;
import com.example.materialpull.resilience.IdempotencyService;
import com.example.materialpull.resilience.OperationGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceDataScopeTest {
    @Mock ReplenishmentTaskRepository taskRepository;
    @Mock BoxRepository boxRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock AuditService auditService;
    @Mock RealtimePushService pushService;
    @Mock SystemAlertService alertService;
    @Mock BusinessLockService lockService;
    @Mock IdempotencyService idempotencyService;
    @Mock OperationGuard guard;
    @Mock AppProperties properties;
    @Mock PrintJobService printJobService;
    @Mock BoxPoolService boxPoolService;
    @Mock AgvService agvService;
    @Mock DataScopeService dataScopeService;

    @InjectMocks TaskService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void scopedListUsesFactoryAndAuthorizedAreasInRepositoryQuery() {
        List<String> areas = List.of("T26 Floor", "EOVA IP");
        List<ReplenishmentTaskEntity> expected = List.of(new ReplenishmentTaskEntity());
        when(dataScopeService.isGlobalAdmin()).thenReturn(false);
        when(dataScopeService.currentFactory()).thenReturn("弋江");
        when(dataScopeService.currentDeliveryAreas()).thenReturn(areas);
        when(taskRepository.findTop1000ByFactoryAndDeliveryAreaInOrderByCreatedAtDesc("弋江", areas))
                .thenReturn(expected);

        assertEquals(expected, service.list(null, null));

        verify(taskRepository).findTop1000ByFactoryAndDeliveryAreaInOrderByCreatedAtDesc("弋江", areas);
        verify(taskRepository, never()).findTop1000ByOrderByCreatedAtDesc();
    }

    @Test
    void scopedStatusListKeepsStatusAndScopeInSameRepositoryQuery() {
        List<String> areas = List.of("T26 Floor");
        when(dataScopeService.isGlobalAdmin()).thenReturn(false);
        when(dataScopeService.currentFactory()).thenReturn("弋江");
        when(dataScopeService.currentDeliveryAreas()).thenReturn(areas);

        service.list("created", null);

        verify(taskRepository).findTop1000ByFactoryAndDeliveryAreaInAndStatusOrderByCreatedAtDesc(
                "弋江", areas, TaskStatus.CREATED);
        verify(taskRepository, never()).findTop1000ByStatusOrderByCreatedAtDesc(any());
    }

    @Test
    void scopedPrintableUsesScopeInRepositoryQuery() {
        List<String> areas = List.of("T26 Floor");
        when(dataScopeService.isGlobalAdmin()).thenReturn(false);
        when(dataScopeService.currentFactory()).thenReturn("弋江");
        when(dataScopeService.currentDeliveryAreas()).thenReturn(areas);

        service.printable();

        verify(taskRepository).findByFactoryAndDeliveryAreaInAndStatusInAndPrintGeneratedFalseAndPrintJobNoIsNullOrderByCreatedAtAsc(
                eq("弋江"), eq(areas), argThat(statuses -> statuses.contains(TaskStatus.CREATED)
                        && statuses.contains(TaskStatus.PICKED)));
        verify(taskRepository, never())
                .findByStatusInAndPrintGeneratedFalseAndPrintJobNoIsNullOrderByCreatedAtAsc(any());
    }

    @Test
    void globalAdminListKeepsGlobalRepositoryPath() {
        when(dataScopeService.isGlobalAdmin()).thenReturn(true);

        service.list(null, null);

        verify(taskRepository).findTop1000ByOrderByCreatedAtDesc();
        verify(dataScopeService, never()).currentFactory();
        verify(dataScopeService, never()).currentDeliveryAreas();
    }

    @Test
    void deleteRejectsOutOfScopeTaskBeforeAnyBusinessMutation() {
        RequestContext.setLoginUser(10L, "warehouse", "仓库", UserRole.WAREHOUSE,
                "弋江", List.of("T26 Floor"));
        ReplenishmentTaskEntity task = task("RP-OTHER", "三山", "A区");
        when(guard.notBlank("RP-OTHER", "任务号")).thenReturn("RP-OTHER");
        when(taskRepository.findByTaskNoForUpdate("RP-OTHER")).thenReturn(Optional.of(task));
        executeLockSupplier();
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该数据"))
                .when(dataScopeService).requireAccessFactoryArea("三山", "A区");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteTask("RP-OTHER"));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(printJobService, boxPoolService, agvService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "startPick", "picked", "deliver", "arrive", "complete",
            "receive", "returnEmptyBox", "cancel", "exception", "retry", "forceComplete", "remark", "archive"})
    void everyActionRejectsOutOfScopeTaskBeforeBusinessMutation(String action) {
        RequestContext.setLoginUser(11L, "admin", "管理员", UserRole.ADMIN,
                "弋江", List.of("T26 Floor"));
        ReplenishmentTaskEntity task = task("RP-OTHER", "三山", "A区");
        task.setRemark("原备注");
        when(guard.notBlank("RP-OTHER", "任务号")).thenReturn("RP-OTHER");
        when(guard.notBlank(action, "操作")).thenReturn(action);
        when(taskRepository.findByTaskNoForUpdate("RP-OTHER")).thenReturn(Optional.of(task));
        executeLockSupplier();
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该数据"))
                .when(dataScopeService).requireAccessFactoryArea("三山", "A区");
        TaskActionRequest request = new TaskActionRequest();
        request.remark = "越权修改";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.action("RP-OTHER", action, request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals("原备注", task.getRemark());
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(printJobService, boxPoolService, agvService, inventoryRepository, boxRepository);
    }

    private ReplenishmentTaskEntity task(String taskNo, String factory, String area) {
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setTaskNo(taskNo);
        task.setFactory(factory);
        task.setDeliveryArea(area);
        task.setStatus(TaskStatus.CREATED);
        return task;
    }

    @SuppressWarnings("unchecked")
    private void executeLockSupplier() {
        when(lockService.execute(anyString(), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
    }
}
