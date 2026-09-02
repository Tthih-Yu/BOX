package com.example.materialpull.repository;

import com.example.materialpull.entity.InterfaceLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface InterfaceLogRepository extends JpaRepository<InterfaceLogEntity, Long>, JpaSpecificationExecutor<InterfaceLogEntity> {
    List<InterfaceLogEntity> findTop1000ByOrderByCreatedAtDesc();
}
