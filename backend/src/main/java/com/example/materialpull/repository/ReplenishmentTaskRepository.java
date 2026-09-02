package com.example.materialpull.repository;

import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.enums.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.*;

public interface ReplenishmentTaskRepository extends JpaRepository<ReplenishmentTaskEntity, Long> {
    Optional<ReplenishmentTaskEntity> findByTaskNo(String taskNo);
    List<ReplenishmentTaskEntity> findByTaskNoIn(Collection<String> taskNos);
    List<ReplenishmentTaskEntity> findTop20ByOrderByCreatedAtDesc();
    List<ReplenishmentTaskEntity> findTop1000ByOrderByCreatedAtDesc();
    List<ReplenishmentTaskEntity> findTop1000ByStatusOrderByCreatedAtDesc(TaskStatus status);
    List<ReplenishmentTaskEntity> findTop1000ByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);
    List<ReplenishmentTaskEntity> findTop1000ByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(TaskStatus status, LocalDateTime from, LocalDateTime to);
    List<ReplenishmentTaskEntity> findTop1000ByFactoryAndDeliveryAreaInOrderByCreatedAtDesc(String factory, Collection<String> deliveryAreas);
    List<ReplenishmentTaskEntity> findTop1000ByFactoryAndDeliveryAreaInAndStatusOrderByCreatedAtDesc(String factory, Collection<String> deliveryAreas, TaskStatus status);
    List<ReplenishmentTaskEntity> findTop1000ByFactoryAndDeliveryAreaInAndCreatedAtBetweenOrderByCreatedAtDesc(String factory, Collection<String> deliveryAreas, LocalDateTime from, LocalDateTime to);
    List<ReplenishmentTaskEntity> findTop1000ByFactoryAndDeliveryAreaInAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(String factory, Collection<String> deliveryAreas, TaskStatus status, LocalDateTime from, LocalDateTime to);
    List<ReplenishmentTaskEntity> findTop1000ByFactoryOrderByCreatedAtDesc(String factory);
    List<ReplenishmentTaskEntity> findTop1000ByFactoryAndStatusOrderByCreatedAtDesc(String factory, TaskStatus status);
    List<ReplenishmentTaskEntity> findTop1000ByFactoryAndCreatedAtBetweenOrderByCreatedAtDesc(String factory, LocalDateTime from, LocalDateTime to);
    List<ReplenishmentTaskEntity> findTop1000ByFactoryAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(String factory, TaskStatus status, LocalDateTime from, LocalDateTime to);
    List<ReplenishmentTaskEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);
    long countByStatus(TaskStatus status);
    long countByFactoryIgnoreCaseAndDeliveryAreaInAndStatus(String factory, Collection<String> deliveryAreas, TaskStatus status);
    long countByStatusAndCreatedAtAfter(TaskStatus status, LocalDateTime createdAt);
    long countByFactoryIgnoreCaseAndDeliveryAreaInAndStatusAndCreatedAtAfter(
            String factory, Collection<String> deliveryAreas, TaskStatus status, LocalDateTime createdAt);
    List<ReplenishmentTaskEntity> findByStatusIn(List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByFactoryIgnoreCaseAndDeliveryAreaInAndStatusIn(
            String factory, Collection<String> deliveryAreas, List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findTop20ByFactoryIgnoreCaseAndDeliveryAreaInOrderByCreatedAtDesc(
            String factory, Collection<String> deliveryAreas);
    List<ReplenishmentTaskEntity> findByFactoryIgnoreCaseAndDeliveryAreaInAndCreatedAtBetweenOrderByCreatedAtDesc(
            String factory, Collection<String> deliveryAreas, LocalDateTime from, LocalDateTime to);
    List<ReplenishmentTaskEntity> findTop500ByStatusInAndPrintGeneratedFalseOrderByCreatedAtAsc(List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByStatusInAndPrintGeneratedFalseAndPrintJobNoIsNullOrderByCreatedAtAsc(Collection<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByFactoryAndDeliveryAreaInAndStatusInAndPrintGeneratedFalseAndPrintJobNoIsNullOrderByCreatedAtAsc(
            String factory, Collection<String> deliveryAreas, Collection<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByFactoryAndStatusInAndPrintGeneratedFalseAndPrintJobNoIsNullOrderByCreatedAtAsc(
            String factory, Collection<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByStatusInAndUpdatedAtBefore(List<TaskStatus> statuses, LocalDateTime before);
    List<ReplenishmentTaskEntity> findBySourceLabelCodeAndStatusIn(String sourceLabelCode, List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByFactoryAndDeliveryAreaAndSourceLabelCodeAndStatusIn(
            String factory, String deliveryArea, String sourceLabelCode, List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByMaterialCodeAndStatusIn(String materialCode, List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByWarehouseCodeAndStatusIn(String warehouseCode, List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByFactoryAndDeliveryAreaAndWarehouseCodeAndStatusIn(
            String factory, String deliveryArea, String warehouseCode, List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByBoxCodeAndStatusIn(String boxCode, List<TaskStatus> statuses);
    boolean existsBySourceLabelCodeAndStatusIn(String sourceLabelCode, List<TaskStatus> statuses);
    boolean existsByPlanNo(String planNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReplenishmentTaskEntity t where t.taskNo = :taskNo")
    Optional<ReplenishmentTaskEntity> findByTaskNoForUpdate(@Param("taskNo") String taskNo);
}
