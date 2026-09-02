package com.example.materialpull.repository;

import com.example.materialpull.entity.InventoryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {
    Optional<InventoryEntity> findFirstByWarehouseMaterialCodeAndFactoryIsNullAndDeliveryAreaIsNullOrderByUpdatedAtDesc(String warehouseMaterialCode);
    List<InventoryEntity> findByWarehouseMaterialCode(String warehouseMaterialCode);
    List<InventoryEntity> findByFactoryIgnoreCaseAndWarehouseMaterialCode(String factory, String warehouseMaterialCode);
    List<InventoryEntity> findTop1000ByFactoryIgnoreCaseAndDeliveryAreaInOrderByUpdatedAtDesc(String factory, Collection<String> deliveryAreas);
    @Query("select i from InventoryEntity i where coalesce(i.availableQty, 0) < coalesce(i.safetyStock, 0) order by i.updatedAt desc")
    List<InventoryEntity> findLowStock();
    @Query("select i from InventoryEntity i where lower(i.factory) = lower(:factory) and i.deliveryArea in :deliveryAreas and coalesce(i.availableQty, 0) < coalesce(i.safetyStock, 0) order by i.updatedAt desc")
    List<InventoryEntity> findLowStockScoped(@Param("factory") String factory,
                                             @Param("deliveryAreas") Collection<String> deliveryAreas);
    boolean existsByFactoryIgnoreCaseAndDeliveryAreaAndWarehouseCodeAndLocationCodeAndWarehouseMaterialCodeAndIdNot(
            String factory, String deliveryArea, String warehouseCode, String locationCode, String warehouseMaterialCode, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryEntity i where i.warehouseMaterialCode = :warehouseMaterialCode order by i.updatedAt desc")
    List<InventoryEntity> findByWarehouseMaterialCodeForUpdate(@Param("warehouseMaterialCode") String warehouseMaterialCode);
}
