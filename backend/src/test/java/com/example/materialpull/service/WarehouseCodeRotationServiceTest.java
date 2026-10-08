package com.example.materialpull.service;

import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.entity.WarehouseCodeRotationStateEntity;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import com.example.materialpull.repository.WarehouseCodeRotationStateRepository;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarehouseCodeRotationServiceTest {

    private final WarehouseCodeRotationStateRepository stateRepository = mock(WarehouseCodeRotationStateRepository.class);
    private final ReplenishmentTaskRepository taskRepository = mock(ReplenishmentTaskRepository.class);
    private final WarehouseCodeRotationService service = new WarehouseCodeRotationService(stateRepository, taskRepository);

    @Test
    void twoCodesAlternateEightTimes() {
        AtomicReference<WarehouseCodeRotationStateEntity> stored = stateStore();
        List<MaterialMappingEntity> mappings = mappings("工位-A", "119949", "119950");

        List<String> actual = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            WarehouseCodeRotationService.Selection selection = service.begin(mappings, "15385994", "工位-A");
            actual.add(selection.selectedWarehouseCode());
            service.markTaskSaved(selection, "TASK-" + i);
        }

        assertEquals(List.of("119949", "119950", "119949", "119950", "119949", "119950", "119949", "119950"), actual);
        assertEquals(8L, stored.get().getSequenceNo());
    }

    @Test
    void moreThanTwoCodesUseDeterministicRoundRobin() {
        stateStore();
        List<MaterialMappingEntity> mappings = mappings("工位-A", "A", "B", "C", "D");

        List<String> actual = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            WarehouseCodeRotationService.Selection selection = service.begin(mappings, "MAT-4", "工位-A");
            actual.add(selection.selectedWarehouseCode());
            service.markTaskSaved(selection, "TASK-" + i);
        }

        assertEquals(List.of("A", "B", "C", "D", "A", "B", "C", "D", "A"), actual);
    }

    @Test
    void sameMaterialAtDifferentStationsKeepsIndependentState() {
        Map<String, WarehouseCodeRotationStateEntity> states = new ConcurrentHashMap<>();
        when(stateRepository.findForUpdate(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(states.get(invocation.getArgument(1))));
        when(stateRepository.save(any())).thenAnswer(invocation -> {
            WarehouseCodeRotationStateEntity state = invocation.getArgument(0);
            states.put(state.getStationKey(), state);
            return state;
        });
        when(taskRepository.findRotationHistory(anyString(), anyString(), anyCollection(), any()))
                .thenReturn(List.of());
        List<MaterialMappingEntity> stationA = mappings("工位-A", "A1", "A2");
        List<MaterialMappingEntity> stationB = mappings("工位-B", "B1", "B2");

        WarehouseCodeRotationService.Selection a1 = service.begin(stationA, "MAT", "工位-A");
        service.markTaskSaved(a1, "TASK-A1");
        WarehouseCodeRotationService.Selection b1 = service.begin(stationB, "MAT", "工位-B");
        service.markTaskSaved(b1, "TASK-B1");
        WarehouseCodeRotationService.Selection a2 = service.begin(stationA, "MAT", "工位-A");

        assertEquals("A1", a1.selectedWarehouseCode());
        assertEquals("B1", b1.selectedWarehouseCode());
        assertEquals("A2", a2.selectedWarehouseCode());
    }

    @Test
    void failedGenerationDoesNotAdvanceState() {
        stateStore();
        List<MaterialMappingEntity> mappings = mappings("工位-A", "A", "B");

        WarehouseCodeRotationService.Selection failed = service.begin(mappings, "MAT", "工位-A");
        WarehouseCodeRotationService.Selection retry = service.begin(mappings, "MAT", "工位-A");

        assertEquals("A", failed.selectedWarehouseCode());
        assertEquals("A", retry.selectedWarehouseCode());
    }

    @Test
    void removedLastCodeRestartsFromCurrentFirstCode() {
        WarehouseCodeRotationStateEntity stale = new WarehouseCodeRotationStateEntity();
        stale.setLastWarehouseCode("OLD");
        stale.setSequenceNo(7L);
        when(stateRepository.findForUpdate(anyString(), anyString(), anyString())).thenReturn(Optional.of(stale));

        WarehouseCodeRotationService.Selection selection = service.begin(mappings("工位-A", "A", "B"), "MAT", "工位-A");

        assertEquals("A", selection.selectedWarehouseCode());
        assertEquals(8L, selection.sequenceNo());
    }

    @Test
    void duplicateWarehouseCodesAreTreatedAsSingleCode() {
        List<MaterialMappingEntity> mappings = mappings("工位-A", "A", "A");

        WarehouseCodeRotationService.Selection selection = service.begin(mappings, "MAT", "工位-A");
        service.markTaskSaved(selection, "TASK-1");

        assertEquals(List.of("A"), selection.candidateCodes());
        verifyNoInteractions(stateRepository, taskRepository);
    }

    @Test
    void firstUseContinuesFromLatestPrintedTask() {
        when(stateRepository.findForUpdate(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        ReplenishmentTaskEntity history = new ReplenishmentTaskEntity();
        history.setDeliveryAddress("工位-A");
        history.setWarehouseCode("A");
        when(taskRepository.findRotationHistory(eq("弋江"), eq("MAT"), eq(List.of("A", "B")), any()))
                .thenReturn(List.of(history));

        WarehouseCodeRotationService.Selection selection = service.begin(mappings("工位-A", "A", "B"), "MAT", "工位-A");

        assertEquals("B", selection.selectedWarehouseCode());
    }

    private AtomicReference<WarehouseCodeRotationStateEntity> stateStore() {
        AtomicReference<WarehouseCodeRotationStateEntity> stored = new AtomicReference<>();
        when(stateRepository.findForUpdate(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(stateRepository.save(any())).thenAnswer(invocation -> {
            WarehouseCodeRotationStateEntity state = invocation.getArgument(0);
            stored.set(state);
            return state;
        });
        when(taskRepository.findRotationHistory(anyString(), anyString(), anyCollection(), any()))
                .thenReturn(List.of());
        return stored;
    }

    private List<MaterialMappingEntity> mappings(String station, String... codes) {
        List<MaterialMappingEntity> result = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            MaterialMappingEntity mapping = new MaterialMappingEntity();
            mapping.setId((long) i + 1);
            mapping.setFactory("弋江");
            mapping.setDeliveryArea("测试区");
            mapping.setDeliveryAddress(station);
            mapping.setLineMaterialCode("MAT");
            mapping.setWarehouseCode(codes[i]);
            mapping.setWarehouseMaterialCode(codes[i]);
            mapping.setMappingOrder(i + 1);
            mapping.setEnabled(true);
            result.add(mapping);
        }
        return result;
    }
}
