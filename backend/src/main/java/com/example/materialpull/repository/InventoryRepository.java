package com.example.materialpull.repository;

import com.example.materialpull.entity.InventoryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {
    Optional<InventoryEntity> findFirstByWarehouseMaterialCodeOrderByUpdatedAtDesc(String warehouseMaterialCode);
    List<InventoryEntity> findByWarehouseMaterialCode(String warehouseMaterialCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryEntity i where i.warehouseMaterialCode = :warehouseMaterialCode order by i.updatedAt desc")
    List<InventoryEntity> findByWarehouseMaterialCodeForUpdate(@Param("warehouseMaterialCode") String warehouseMaterialCode);
}
