package com.example.materialpull.repository;

import com.example.materialpull.entity.PrintJobEntity;
import com.example.materialpull.enums.PrintJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PrintJobRepository extends JpaRepository<PrintJobEntity, Long> {
    Optional<PrintJobEntity> findByPrintJobNo(String printJobNo);
    List<PrintJobEntity> findByTaskNo(String taskNo);
    List<PrintJobEntity> findByStatus(PrintJobStatus status);
    List<PrintJobEntity> findTop1000ByOrderByCreatedAtDesc();
    List<PrintJobEntity> findTop1000ByStatusOrderByCreatedAtDesc(PrintJobStatus status);
}
