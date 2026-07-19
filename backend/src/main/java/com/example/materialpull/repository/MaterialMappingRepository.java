package com.example.materialpull.repository;

import com.example.materialpull.entity.MaterialMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MaterialMappingRepository extends JpaRepository<MaterialMappingEntity, Long> {
    Optional<MaterialMappingEntity> findByLineMaterialCodeAndEnabledTrue(String lineMaterialCode);
    List<MaterialMappingEntity> findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc(String lineMaterialCode);
    Optional<MaterialMappingEntity> findFirstByLineMaterialCodeAndDeliveryTypeAndEnabledTrueOrderByMappingOrderAscIdAsc(String lineMaterialCode, String deliveryType);
    Optional<MaterialMappingEntity> findByWarehouseCodeAndEnabledTrue(String warehouseCode);
    List<MaterialMappingEntity> findTop1000ByOrderByLineMaterialCodeAscMappingOrderAscIdAsc();
}
