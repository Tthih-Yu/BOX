package com.example.materialpull.service;

import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import com.example.materialpull.repository.WarehouseCodeRotationStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(WarehouseCodeRotationService.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class WarehouseCodeRotationConcurrencyTest {
    @Autowired MaterialMappingRepository mappingRepository;
    @Autowired ReplenishmentTaskRepository taskRepository;
    @Autowired WarehouseCodeRotationStateRepository stateRepository;
    @Autowired WarehouseCodeRotationService rotationService;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsNeverPersistAdjacentEqualCodes() throws Exception {
        saveMappings("MAT-CONCURRENT", "工位-A", "A", "B");
        int requestCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Choice>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                int requestNo = i;
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return inTransaction(() -> {
                        List<MaterialMappingEntity> locked = mappingRepository
                                .findByLineMaterialCodeForRotationUpdate("MAT-CONCURRENT");
                        WarehouseCodeRotationService.Selection selection = rotationService
                                .begin(locked, "MAT-CONCURRENT", "工位-A");
                        ReplenishmentTaskEntity task = task("TASK-C-" + requestNo, "MAT-CONCURRENT",
                                "工位-A", selection.selectedWarehouseCode());
                        taskRepository.saveAndFlush(task);
                        rotationService.markTaskSaved(selection, task.getTaskNo());
                        return new Choice(selection.sequenceNo(), selection.selectedWarehouseCode());
                    });
                }));
            }
            start.countDown();
            List<Choice> choices = new ArrayList<>();
            for (Future<Choice> future : futures) choices.add(future.get(15, TimeUnit.SECONDS));
            choices.sort(Comparator.comparingLong(Choice::sequence));

            assertEquals(requestCount, choices.size());
            for (int i = 0; i < choices.size(); i++) {
                assertEquals(i + 1L, choices.get(i).sequence());
                assertEquals(i % 2 == 0 ? "A" : "B", choices.get(i).code());
                if (i > 0) assertNotEquals(choices.get(i - 1).code(), choices.get(i).code());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollbackDoesNotAdvanceRotationState() {
        saveMappings("MAT-ROLLBACK", "工位-B", "A", "B");

        assertThrows(RuntimeException.class, () -> inTransaction(() -> {
            List<MaterialMappingEntity> locked = mappingRepository
                    .findByLineMaterialCodeForRotationUpdate("MAT-ROLLBACK");
            WarehouseCodeRotationService.Selection selection = rotationService
                    .begin(locked, "MAT-ROLLBACK", "工位-B");
            ReplenishmentTaskEntity task = task("TASK-ROLLBACK", "MAT-ROLLBACK", "工位-B",
                    selection.selectedWarehouseCode());
            taskRepository.saveAndFlush(task);
            rotationService.markTaskSaved(selection, task.getTaskNo());
            throw new RuntimeException("模拟标签生成后的事务失败");
        }));

        Choice retry = inTransaction(() -> {
            List<MaterialMappingEntity> locked = mappingRepository
                    .findByLineMaterialCodeForRotationUpdate("MAT-ROLLBACK");
            WarehouseCodeRotationService.Selection selection = rotationService
                    .begin(locked, "MAT-ROLLBACK", "工位-B");
            return new Choice(selection.sequenceNo(), selection.selectedWarehouseCode());
        });
        assertEquals(new Choice(1L, "A"), retry);
    }

    private void saveMappings(String material, String station, String... codes) {
        inTransaction(() -> {
            for (int i = 0; i < codes.length; i++) {
                MaterialMappingEntity mapping = new MaterialMappingEntity();
                mapping.setFactory("弋江");
                mapping.setDeliveryArea("测试区");
                mapping.setDeliveryAddress(station);
                mapping.setLineMaterialCode(material);
                mapping.setWarehouseCode(codes[i]);
                mapping.setWarehouseMaterialCode(codes[i]);
                mapping.setQuantity(BigDecimal.ONE);
                mapping.setMappingOrder(i + 1);
                mapping.setEnabled(true);
                mappingRepository.save(mapping);
            }
            mappingRepository.flush();
            return null;
        });
    }

    private ReplenishmentTaskEntity task(String taskNo, String material, String station, String code) {
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setTaskNo(taskNo);
        task.setFactory("弋江");
        task.setDeliveryArea("测试区");
        task.setStationCode(station);
        task.setDeliveryAddress(station);
        task.setSendStationAddress(station);
        task.setMaterialCode(material);
        task.setWarehouseCode(code);
        task.setBarcodeValue(code);
        task.setRequestQty(BigDecimal.ONE);
        return task;
    }

    private <T> T inTransaction(Callable<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private record Choice(long sequence, String code) {}
}
