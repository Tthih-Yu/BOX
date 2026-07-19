package com.example.materialpull.repository;

import com.example.materialpull.entity.InterfaceLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InterfaceLogRepository extends JpaRepository<InterfaceLogEntity, Long> {
    List<InterfaceLogEntity> findTop1000ByOrderByCreatedAtDesc();
}
