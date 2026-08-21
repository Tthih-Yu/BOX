package com.example.materialpull.repository;

import com.example.materialpull.entity.MaterialMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface MaterialMappingRepository extends JpaRepository<MaterialMappingEntity, Long> {
    Optional<MaterialMappingEntity> findByLineMaterialCodeAndEnabledTrue(String lineMaterialCode);
    List<MaterialMappingEntity> findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc(String lineMaterialCode);
    Optional<MaterialMappingEntity> findFirstByLineMaterialCodeAndDeliveryTypeAndEnabledTrueOrderByMappingOrderAscIdAsc(String lineMaterialCode, String deliveryType);
    Optional<MaterialMappingEntity> findByWarehouseCodeAndEnabledTrue(String warehouseCode);
    List<MaterialMappingEntity> findByWarehouseCodeInAndEnabledTrue(Collection<String> warehouseCodes);
    List<MaterialMappingEntity> findTop1000ByOrderByLineMaterialCodeAscMappingOrderAscIdAsc();
    List<MaterialMappingEntity> findAllByOrderByLineMaterialCodeAscMappingOrderAscIdAsc();
    Page<MaterialMappingEntity> findByLineMaterialCodeContainingIgnoreCaseOrWarehouseCodeContainingIgnoreCaseOrWarehouseMaterialCodeContainingIgnoreCaseOrDeliveryAddressContainingIgnoreCaseOrderByLineMaterialCodeAscMappingOrderAscIdAsc(String lineMaterialCode, String warehouseCode, String warehouseMaterialCode, String deliveryAddress, Pageable pageable);
    long countByDeliveryArea(String deliveryArea);

    @Modifying
    @Query("delete from MaterialMappingEntity m where m.deliveryArea = :deliveryArea")
    int deleteByDeliveryArea(@Param("deliveryArea") String deliveryArea);
}
