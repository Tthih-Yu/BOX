package com.example.materialpull.repository;

import com.example.materialpull.entity.AgvJobEntity;
import com.example.materialpull.enums.AgvJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AgvJobRepository extends JpaRepository<AgvJobEntity, Long> {
    Optional<AgvJobEntity> findByAgvJobNo(String agvJobNo);
    List<AgvJobEntity> findByTaskNo(String taskNo);
    List<AgvJobEntity> findByStatus(AgvJobStatus status);
    List<AgvJobEntity> findTop1000ByOrderByCreatedAtDesc();
    List<AgvJobEntity> findTop1000ByStatusOrderByCreatedAtDesc(AgvJobStatus status);
}
