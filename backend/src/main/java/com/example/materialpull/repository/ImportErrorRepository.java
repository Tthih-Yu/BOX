package com.example.materialpull.repository;

import com.example.materialpull.entity.ImportErrorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ImportErrorRepository extends JpaRepository<ImportErrorEntity, Long> {
    List<ImportErrorEntity> findByBatchNoOrderByRowNoAsc(String batchNo);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from ImportErrorEntity e where e.batchNo = :batchNo")
    int deleteByBatchNo(@org.springframework.data.repository.query.Param("batchNo") String batchNo);
}
