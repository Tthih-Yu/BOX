package com.example.materialpull.repository;

import com.example.materialpull.entity.ImportErrorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ImportErrorRepository extends JpaRepository<ImportErrorEntity, Long> {
    List<ImportErrorEntity> findByBatchNoOrderByRowNoAsc(String batchNo);
}
