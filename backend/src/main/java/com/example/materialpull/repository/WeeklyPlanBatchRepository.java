package com.example.materialpull.repository;

import com.example.materialpull.entity.WeeklyPlanBatchEntity;
import com.example.materialpull.enums.WeeklyPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeeklyPlanBatchRepository extends JpaRepository<WeeklyPlanBatchEntity, Long> {
    Optional<WeeklyPlanBatchEntity> findByBatchNo(String batchNo);
    List<WeeklyPlanBatchEntity> findTop200ByOrderByIdDesc();
    List<WeeklyPlanBatchEntity> findTop200ByFactoryIgnoreCaseOrderByIdDesc(String factory);
    List<WeeklyPlanBatchEntity> findByPlanYearAndWeekNoAndFactoryIgnoreCaseAndStatus(
            Integer planYear, Integer weekNo, String factory, WeeklyPlanStatus status);
}
