package com.example.materialpull.repository;

import com.example.materialpull.entity.MaterialDemandEntity;
import com.example.materialpull.enums.DemandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MaterialDemandRepository extends JpaRepository<MaterialDemandEntity, Long> {
    Optional<MaterialDemandEntity> findByDemandNo(String demandNo);
    List<MaterialDemandEntity> findByPlanNo(String planNo);
    boolean existsByPlanNo(String planNo);
    List<MaterialDemandEntity> findByStatus(DemandStatus status);
}
