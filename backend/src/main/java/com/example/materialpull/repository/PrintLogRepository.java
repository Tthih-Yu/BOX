package com.example.materialpull.repository;

import com.example.materialpull.entity.PrintLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PrintLogRepository extends JpaRepository<PrintLogEntity, Long> {
    List<PrintLogEntity> findTop1000ByOrderByCreatedAtDesc();
}
