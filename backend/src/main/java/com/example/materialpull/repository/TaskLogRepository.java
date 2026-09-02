package com.example.materialpull.repository;

import com.example.materialpull.entity.TaskLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface TaskLogRepository extends JpaRepository<TaskLogEntity, Long>, JpaSpecificationExecutor<TaskLogEntity> {
    List<TaskLogEntity> findTop1000ByOrderByCreatedAtDesc();
}
