package com.example.materialpull.service;

import com.example.materialpull.entity.OutboxEventEntity;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.entity.ScanLogEntity;
import com.example.materialpull.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScopeSnapshotServiceTest {
    @Mock ScanLogRepository scanLogRepository;
    @Mock TaskLogRepository taskLogRepository;
    @Mock PrintLogRepository printLogRepository;
    @Mock InterfaceLogRepository interfaceLogRepository;
    @Mock OutboxEventRepository outboxEventRepository;

    @Test
    void scanAuditPersistsTrustedScopeSnapshot() {
        AuditService service = new AuditService(scanLogRepository, taskLogRepository, printLogRepository, interfaceLogRepository);

        service.scan("L1", "B1", "EMPTY", true, "ok", "u", "d", "s", "m", "弋江", "T26 Floor");

        ArgumentCaptor<ScanLogEntity> captor = ArgumentCaptor.forClass(ScanLogEntity.class);
        verify(scanLogRepository).save(captor.capture());
        assertEquals("弋江", captor.getValue().getFactory());
        assertEquals("T26 Floor", captor.getValue().getDeliveryArea());
    }

    @Test
    void outboxDerivesFactoryAreaScopeFromBusinessObject() {
        OutboxService service = new OutboxService(outboxEventRepository);
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setFactory("弋江");
        task.setDeliveryArea("T26 Floor");

        service.record("tasks", "T1", task);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        assertEquals("FACTORY_AREA", captor.getValue().getScopeType());
        assertEquals("弋江", captor.getValue().getFactory());
        assertEquals("T26 Floor", captor.getValue().getDeliveryArea());
    }
}
