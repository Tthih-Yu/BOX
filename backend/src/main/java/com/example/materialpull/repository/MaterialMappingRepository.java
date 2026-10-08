package com.example.materialpull.repository;

import com.example.materialpull.dto.MaterialUsageMappingDto;
import com.example.materialpull.entity.MaterialMappingEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface MaterialMappingRepository extends JpaRepository<MaterialMappingEntity, Long>, JpaSpecificationExecutor<MaterialMappingEntity> {
    Optional<MaterialMappingEntity> findByLineMaterialCodeAndEnabledTrue(String lineMaterialCode);
    List<MaterialMappingEntity> findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc(String lineMaterialCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from MaterialMappingEntity m
            where m.lineMaterialCode = :lineMaterialCode and m.enabled = true
            order by m.mappingOrder asc, m.id asc
            """)
    List<MaterialMappingEntity> findByLineMaterialCodeForRotationUpdate(
            @Param("lineMaterialCode") String lineMaterialCode);
    List<MaterialMappingEntity> findByFactoryIgnoreCaseAndLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc(
            String factory, String lineMaterialCode);
    Optional<MaterialMappingEntity> findFirstByLineMaterialCodeAndDeliveryTypeAndEnabledTrueOrderByMappingOrderAscIdAsc(String lineMaterialCode, String deliveryType);
    Optional<MaterialMappingEntity> findByWarehouseCodeAndEnabledTrue(String warehouseCode);
    List<MaterialMappingEntity> findAllByWarehouseCodeAndEnabledTrueOrderByIdAsc(String warehouseCode);
    List<MaterialMappingEntity> findByWarehouseCodeInAndEnabledTrue(Collection<String> warehouseCodes);
    List<MaterialMappingEntity> findTop1000ByOrderByLineMaterialCodeAscMappingOrderAscIdAsc();
    List<MaterialMappingEntity> findAllByOrderByLineMaterialCodeAscMappingOrderAscIdAsc();
    List<MaterialMappingEntity> findByFactoryIgnoreCaseOrderByLineMaterialCodeAscMappingOrderAscIdAsc(String factory);
    List<MaterialMappingEntity> findByFactoryIgnoreCaseAndDeliveryAreaInOrderByLineMaterialCodeAscMappingOrderAscIdAsc(String factory, Collection<String> deliveryAreas);

    @Query("""
            select new com.example.materialpull.dto.MaterialUsageMappingDto(
                m.lineMaterialCode, m.warehouseCode, m.warehouseMaterialCode, m.factory,
                m.stationCode, m.warehouseLocation, m.deliveryAddress, m.deliveryArea)
            from MaterialMappingEntity m
            order by m.lineMaterialCode asc, m.mappingOrder asc, m.id asc
            """)
    List<MaterialUsageMappingDto> findMaterialUsageRows();

    @Query("""
            select new com.example.materialpull.dto.MaterialUsageMappingDto(
                m.lineMaterialCode, m.warehouseCode, m.warehouseMaterialCode, m.factory,
                m.stationCode, m.warehouseLocation, m.deliveryAddress, m.deliveryArea)
            from MaterialMappingEntity m
            where lower(m.factory) = lower(:factory) and m.deliveryArea in :deliveryAreas
            order by m.lineMaterialCode asc, m.mappingOrder asc, m.id asc
            """)
    List<MaterialUsageMappingDto> findScopedMaterialUsageRows(@Param("factory") String factory,
                                                               @Param("deliveryAreas") Collection<String> deliveryAreas);
    Page<MaterialMappingEntity> findByLineMaterialCodeContainingIgnoreCaseOrWarehouseCodeContainingIgnoreCaseOrWarehouseMaterialCodeContainingIgnoreCaseOrDeliveryAddressContainingIgnoreCaseOrderByLineMaterialCodeAscMappingOrderAscIdAsc(String lineMaterialCode, String warehouseCode, String warehouseMaterialCode, String deliveryAddress, Pageable pageable);
    @Query("select m from MaterialMappingEntity m where lower(m.factory) = lower(:factory) and m.deliveryArea in :deliveryAreas and (lower(m.lineMaterialCode) like lower(concat('%', :keyword, '%')) or lower(m.warehouseCode) like lower(concat('%', :keyword, '%')) or lower(m.warehouseMaterialCode) like lower(concat('%', :keyword, '%')) or lower(m.deliveryAddress) like lower(concat('%', :keyword, '%'))) order by m.lineMaterialCode asc, m.mappingOrder asc, m.id asc")
    Page<MaterialMappingEntity> findScoped(@Param("factory") String factory,
                                           @Param("deliveryAreas") Collection<String> deliveryAreas,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);
    @Query("select m from MaterialMappingEntity m where lower(m.factory) = lower(:factory) and (lower(m.lineMaterialCode) like lower(concat('%', :keyword, '%')) or lower(m.warehouseCode) like lower(concat('%', :keyword, '%')) or lower(m.warehouseMaterialCode) like lower(concat('%', :keyword, '%')) or lower(m.deliveryAddress) like lower(concat('%', :keyword, '%'))) order by m.lineMaterialCode asc, m.mappingOrder asc, m.id asc")
    Page<MaterialMappingEntity> findFactoryScoped(@Param("factory") String factory,
                                                   @Param("keyword") String keyword,
                                                   Pageable pageable);
    long countByDeliveryArea(String deliveryArea);

    @Query("select distinct m.deliveryArea from MaterialMappingEntity m where lower(m.factory) = lower(:factory) and m.deliveryArea is not null and trim(m.deliveryArea) <> '' order by m.deliveryArea")
    List<String> findDistinctDeliveryAreasByFactory(@Param("factory") String factory);

    @Modifying
    @Query("delete from MaterialMappingEntity m where lower(m.factory) = lower(:factory) and m.deliveryArea = :deliveryArea")
    int deleteByFactoryAndDeliveryArea(@Param("factory") String factory, @Param("deliveryArea") String deliveryArea);

    @Modifying
    @Query("delete from MaterialMappingEntity m where lower(m.factory) = lower(:factory)")
    int deleteByFactory(@Param("factory") String factory);
}
