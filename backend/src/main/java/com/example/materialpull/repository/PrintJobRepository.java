package com.example.materialpull.repository;

import com.example.materialpull.entity.PrintJobEntity;
import com.example.materialpull.enums.PrintJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface PrintJobRepository extends JpaRepository<PrintJobEntity, Long> {
    Optional<PrintJobEntity> findByPrintJobNo(String printJobNo);
    List<PrintJobEntity> findByTaskNo(String taskNo);
    List<PrintJobEntity> findByStatus(PrintJobStatus status);
    List<PrintJobEntity> findTop1000ByOrderByCreatedAtDesc();
    List<PrintJobEntity> findTop1000ByStatusOrderByCreatedAtDesc(PrintJobStatus status);
    Page<PrintJobEntity> findByStatusOrderByCreatedAtDesc(PrintJobStatus status, Pageable pageable);
    Page<PrintJobEntity> findByPrintChannelIgnoreCaseOrderByCreatedAtDesc(String printChannel, Pageable pageable);
    Page<PrintJobEntity> findByStatusAndPrintChannelIgnoreCaseOrderByCreatedAtDesc(PrintJobStatus status, String printChannel, Pageable pageable);
    List<PrintJobEntity> findTop50ByStatusInOrderByCreatedAtAsc(java.util.Collection<PrintJobStatus> statuses);
}
