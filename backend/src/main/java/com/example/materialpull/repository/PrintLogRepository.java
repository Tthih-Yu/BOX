package com.example.materialpull.repository;

import com.example.materialpull.entity.PrintLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface PrintLogRepository extends JpaRepository<PrintLogEntity, Long>, JpaSpecificationExecutor<PrintLogEntity> {
    List<PrintLogEntity> findTop1000ByOrderByCreatedAtDesc();
}
