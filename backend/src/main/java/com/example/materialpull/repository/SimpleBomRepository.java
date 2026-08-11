package com.example.materialpull.repository;

import com.example.materialpull.entity.SimpleBomEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SimpleBomRepository extends JpaRepository<SimpleBomEntity, Long> {
    Page<SimpleBomEntity> findByBatchNo(String batchNo, Pageable pageable);
    Page<SimpleBomEntity> findByBatchNoAndMaterialCode(String batchNo, String materialCode, Pageable pageable);
    long countByBatchNo(String batchNo);
    List<SimpleBomEntity> findByBatchNoAndMaterialCode(String batchNo, String materialCode);

    @Modifying
    @Query("delete from SimpleBomEntity e where e.batchNo = :batchNo")
    int deleteByBatchNo(@Param("batchNo") String batchNo);
}
