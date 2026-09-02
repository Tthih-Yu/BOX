package com.example.materialpull.repository;

import com.example.materialpull.entity.ProductionPlanEntity;
import com.example.materialpull.enums.PlanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ProductionPlanRepository extends JpaRepository<ProductionPlanEntity, Long> {
    Optional<ProductionPlanEntity> findByPlanNo(String planNo);
    List<ProductionPlanEntity> findByStatus(PlanStatus status);
    List<ProductionPlanEntity> findTop1000ByFactoryIgnoreCaseOrderByIdDesc(String factory);
    List<ProductionPlanEntity> findByFactoryIgnoreCaseAndStatusOrderByIdDesc(String factory, PlanStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductionPlanEntity p where p.planNo = :planNo")
    Optional<ProductionPlanEntity> findByPlanNoForUpdate(@Param("planNo") String planNo);
}
