package com.example.materialpull.repository;

import com.example.materialpull.entity.SystemAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SystemAlertRepository extends JpaRepository<SystemAlertEntity, Long> {
    List<SystemAlertEntity> findTop100ByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    Optional<SystemAlertEntity> findFirstByStatusAndCategoryAndBusinessNoAndTitleOrderByCreatedAtDesc(String status, String category, String businessNo, String title);
}
