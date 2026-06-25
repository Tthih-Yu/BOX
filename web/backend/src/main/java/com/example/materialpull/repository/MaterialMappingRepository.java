package com.example.materialpull.repository;

import com.example.materialpull.entity.MaterialMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MaterialMappingRepository extends JpaRepository<MaterialMappingEntity, Long> {
    Optional<MaterialMappingEntity> findByLineMaterialCodeAndEnabledTrue(String lineMaterialCode);
}
