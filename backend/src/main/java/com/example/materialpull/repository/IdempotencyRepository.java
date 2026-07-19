package com.example.materialpull.repository;

import com.example.materialpull.entity.IdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;

public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, Long> {
    Optional<IdempotencyEntity> findByRequestKey(String requestKey);
    void deleteByExpiresAtBefore(LocalDateTime time);
}
