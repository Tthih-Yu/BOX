package com.example.materialpull.repository;

import com.example.materialpull.entity.SimpleBomBatchEntity;
import com.example.materialpull.enums.SimpleBomBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SimpleBomBatchRepository extends JpaRepository<SimpleBomBatchEntity, Long> {
    Optional<SimpleBomBatchEntity> findByBatchNo(String batchNo);
    Optional<SimpleBomBatchEntity> findFirstByStatus(SimpleBomBatchStatus status);
    List<SimpleBomBatchEntity> findByStatus(SimpleBomBatchStatus status);
    List<SimpleBomBatchEntity> findTop200ByOrderByIdDesc();

    @Modifying
    @Query("update SimpleBomBatchEntity b set b.status = :to where b.status = :from")
    int updateStatus(@Param("from") SimpleBomBatchStatus from, @Param("to") SimpleBomBatchStatus to);
}
