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
    List<ReplenishmentTaskEntity> findTop20ByOrderByCreatedAtDesc();
    List<ReplenishmentTaskEntity> findTop1000ByOrderByCreatedAtDesc();
    List<ReplenishmentTaskEntity> findTop1000ByStatusOrderByCreatedAtDesc(TaskStatus status);
    long countByStatus(TaskStatus status);
    List<ReplenishmentTaskEntity> findByStatusIn(List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByStatusInAndUpdatedAtBefore(List<TaskStatus> statuses, LocalDateTime before);
    List<ReplenishmentTaskEntity> findBySourceLabelCodeAndStatusIn(String sourceLabelCode, List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByMaterialCodeAndStatusIn(String materialCode, List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByWarehouseCodeAndStatusIn(String warehouseCode, List<TaskStatus> statuses);
    List<ReplenishmentTaskEntity> findByBoxCodeAndStatusIn(String boxCode, List<TaskStatus> statuses);
    boolean existsBySourceLabelCodeAndStatusIn(String sourceLabelCode, List<TaskStatus> statuses);
    boolean existsByPlanNo(String planNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReplenishmentTaskEntity t where t.taskNo = :taskNo")
    Optional<ReplenishmentTaskEntity> findByTaskNoForUpdate(@Param("taskNo") String taskNo);
}
