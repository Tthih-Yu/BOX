package com.example.materialpull.repository;

import com.example.materialpull.entity.BoxEntity;
import com.example.materialpull.enums.BoxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface BoxRepository extends JpaRepository<BoxEntity, Long> {
    Optional<BoxEntity> findByBoxCode(String boxCode);
    Optional<BoxEntity> findByLabelCode(String labelCode);
    Optional<BoxEntity> findByBarcodeValue(String barcodeValue);
    Optional<BoxEntity> findByWarehouseCode(String warehouseCode);
    Optional<BoxEntity> findByKanbanCardNo(String kanbanCardNo);
    List<BoxEntity> findByPairCodeOrderByBoxSideAsc(String pairCode);
    long countByStatus(BoxStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BoxEntity b where b.labelCode = :labelCode")
    Optional<BoxEntity> findByLabelCodeForUpdate(@Param("labelCode") String labelCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BoxEntity b where b.boxCode = :boxCode")
    Optional<BoxEntity> findByBoxCodeForUpdate(@Param("boxCode") String boxCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BoxEntity b where b.labelCode = :code or b.barcodeValue = :code or b.warehouseCode = :code or b.kanbanCardNo = :code")
    List<BoxEntity> findByAnyCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BoxEntity b where b.pairCode = :pairCode order by b.boxSide asc")
    List<BoxEntity> findByPairCodeForUpdate(@Param("pairCode") String pairCode);

    List<BoxEntity> findByHealthStatusNot(String healthStatus);
}
