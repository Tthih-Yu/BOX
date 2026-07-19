package com.example.materialpull.repository;

import com.example.materialpull.entity.TaskLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TaskLogRepository extends JpaRepository<TaskLogEntity, Long> {
    List<TaskLogEntity> findTop1000ByOrderByCreatedAtDesc();
}
