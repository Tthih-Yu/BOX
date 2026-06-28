package com.example.materialpull.repository;

import com.example.materialpull.entity.MaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MaterialRepository extends JpaRepository<MaterialEntity, Long> {
    Optional<MaterialEntity> findByMaterialCode(String materialCode);
Optional<MaterialEntity> findByWarehouseMaterialCode(String warehouseMaterialCode);
}
