package com.example.materialpull.repository;

import com.example.materialpull.entity.LabelEntity;
import com.example.materialpull.enums.LabelStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LabelRepository extends JpaRepository<LabelEntity, Long> {
    Optional<LabelEntity> findByLabelCode(String labelCode);
    Optional<LabelEntity> findByBarcodeValue(String barcodeValue);
    Optional<LabelEntity> findByWarehouseCode(String warehouseCode);
    Optional<LabelEntity> findByKanbanCardNo(String kanbanCardNo);
    boolean existsByBarcodeValue(String barcodeValue);
    boolean existsByWarehouseCode(String warehouseCode);
    boolean existsByKanbanCardNo(String kanbanCardNo);
    boolean existsByPrimaryScanValue(String primaryScanValue);
    long countByStatus(LabelStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LabelEntity l where l.labelCode = :code or l.barcodeValue = :code or l.warehouseCode = :code or l.kanbanCardNo = :code or l.primaryScanValue = :code or l.secondaryScanValue = :code")
    List<LabelEntity> findByAnyCodeForUpdate(@Param("code") String code);

    @Query("select l from LabelEntity l where l.labelCode = :code or l.barcodeValue = :code or l.warehouseCode = :code or l.kanbanCardNo = :code or l.primaryScanValue = :code or l.secondaryScanValue = :code")
    List<LabelEntity> findByAnyCode(@Param("code") String code);

    @Query("select l from LabelEntity l where l.labelType = :labelType")
    List<LabelEntity> findByLabelType(@Param("labelType") String labelType);
}
