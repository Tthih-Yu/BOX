package com.example.materialpull.repository;

import com.example.materialpull.entity.ScanLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ScanLogRepository extends JpaRepository<ScanLogEntity, Long> {
    List<ScanLogEntity> findTop1000ByOrderByScanAtDesc();
    List<ScanLogEntity> findTop100ByLabelCodeOrderByScanAtDesc(String labelCode);
}
