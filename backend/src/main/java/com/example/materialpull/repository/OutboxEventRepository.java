package com.example.materialpull.repository;

import com.example.materialpull.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    List<OutboxEventEntity> findTop100ByStatusInAndNextRetryAtBeforeOrderByCreatedAtAsc(List<String> status, LocalDateTime now);
}
