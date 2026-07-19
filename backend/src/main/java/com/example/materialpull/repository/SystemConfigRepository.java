package com.example.materialpull.repository;

import com.example.materialpull.entity.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, Long> {
    Optional<SystemConfigEntity> findByConfigKey(String configKey);
}
