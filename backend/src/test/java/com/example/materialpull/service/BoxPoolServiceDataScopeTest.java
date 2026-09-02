package com.example.materialpull.service;

import com.example.materialpull.common.RequestContext;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoxPoolServiceDataScopeTest {
    @Mock BoxPoolRepository boxPoolRepository;
    @Mock RealtimePushService pushService;
    @Mock ReplenishmentTaskRepository taskRepository;
    @Spy DataScopeService dataScopeService = new DataScopeService();
    @InjectMocks BoxPoolService service;

    @BeforeEach void login() {
        RequestContext.setLoginUser(1L, "warehouse", "仓库", UserRole.WAREHOUSE, "弋江", List.of("T26 Floor"));
    }
    @AfterEach void clear() { RequestContext.clear(); }

    @Test void listKeepsOnlyContainersWhoseTaskIsInScope() {
        BoxPoolEntity own = box("C1", "T1"), other = box("C2", "T2"), unassigned = box("C3", null);
        when(boxPoolRepository.findTop1000ByOrderByUpdatedAtDesc()).thenReturn(List.of(own, other, unassigned));
        when(taskRepository.findByTaskNoIn(List.of("T1", "T2"))).thenReturn(List.of(task("T1", "弋江"), task("T2", "三山")));
        assertEquals(List.of(own), service.list(null));
    }

    @Test void manualUnassignedSaveRequiresGlobalAdmin() {
        assertThrows(RuntimeException.class, () -> service.save(box("C1", null)));
        verify(boxPoolRepository, never()).save(any());
    }

    @Test void backToWarehouseRejectsContainerOwnedByOtherFactory() {
        BoxPoolEntity other = box("C2", "T2"); other.setId(2L);
        when(boxPoolRepository.findByContainerNoForUpdate("C2")).thenReturn(java.util.Optional.of(other));
        when(taskRepository.findByTaskNo("T2")).thenReturn(java.util.Optional.of(task("T2", "三山")));

        assertThrows(RuntimeException.class, () -> service.backToWarehouse("C2", null));

        verify(boxPoolRepository, never()).save(any());
    }

    @Test void returnEmptyRejectsCrossScopeTargetTask() {
        when(boxPoolRepository.findByContainerNoForUpdate("NEW")).thenReturn(java.util.Optional.empty());
        when(taskRepository.findByTaskNo("T2")).thenReturn(java.util.Optional.of(task("T2", "三山")));

        assertThrows(RuntimeException.class, () -> service.returnEmpty("NEW", "T2", null, "warehouse"));

        verify(boxPoolRepository, never()).save(any());
    }

    private BoxPoolEntity box(String no, String taskNo) {
        BoxPoolEntity box = new BoxPoolEntity(); box.setContainerNo(no); box.setTaskNo(taskNo); return box;
    }
    private ReplenishmentTaskEntity task(String no, String factory) {
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity(); task.setTaskNo(no);
        task.setFactory(factory); task.setDeliveryArea("T26 Floor"); return task;
    }
}
