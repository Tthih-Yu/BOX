package com.example.materialpull.service;

import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BarcodeVariantAssignmentServiceTest {

    private final ReplenishmentTaskRepository repository = mock(ReplenishmentTaskRepository.class);
    private final BarcodeVariantAssignmentService service = new BarcodeVariantAssignmentService(repository);

    @Test
    void assignsNextNumberWithinSameWarehouseCode() {
        ReplenishmentTaskEntity previous = task("139315", 36);
        when(repository.findFirstByWarehouseCodeAndBarcodeVariantNoIsNotNullOrderByBarcodeVariantNoDesc("139315"))
                .thenReturn(Optional.of(previous));
        ReplenishmentTaskEntity task = task("139315", null);

        service.assignIfNeeded(task);

        assertEquals(37, task.getBarcodeVariantNo());
    }

    @Test
    void firstTaskStartsAtZero() {
        when(repository.findFirstByWarehouseCodeAndBarcodeVariantNoIsNotNullOrderByBarcodeVariantNoDesc("139315"))
                .thenReturn(Optional.empty());
        ReplenishmentTaskEntity task = task("139315", null);

        service.assignIfNeeded(task);

        assertEquals(0, task.getBarcodeVariantNo());
    }

    @Test
    void ignoresHistoricalNonSixDigitCode() {
        ReplenishmentTaskEntity task = task("WH-100", null);

        service.assignIfNeeded(task);

        assertNull(task.getBarcodeVariantNo());
        verifyNoInteractions(repository);
    }

    @Test
    void keepsSequenceIncreasingAfterCompactPoolIsExhausted() {
        ReplenishmentTaskEntity previous = task("139315", Code128VariantEncoder.sixDigitVariantCapacity() - 1);
        when(repository.findFirstByWarehouseCodeAndBarcodeVariantNoIsNotNullOrderByBarcodeVariantNoDesc("139315"))
                .thenReturn(Optional.of(previous));
        ReplenishmentTaskEntity task = task("139315", null);

        service.assignIfNeeded(task);

        assertEquals(Code128VariantEncoder.sixDigitVariantCapacity(), task.getBarcodeVariantNo());
    }

    private ReplenishmentTaskEntity task(String warehouseCode, Integer variantNo) {
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setWarehouseCode(warehouseCode);
        task.setBarcodeVariantNo(variantNo);
        return task;
    }
}
