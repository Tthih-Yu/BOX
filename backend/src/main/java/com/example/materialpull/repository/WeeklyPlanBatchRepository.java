package com.example.materialpull.repository;

import com.example.materialpull.entity.WeeklyPlanBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeeklyPlanBatchRepository extends JpaRepository<WeeklyPlanBatchEntity, Long> {
    Optional<WeeklyPlanBatchEntity> findByBatchNo(String batchNo);
    List<WeeklyPlanBatchEntity> findTop200ByOrderByIdDesc();
}
