package com.example.materialpull.repository;

import com.example.materialpull.entity.WarehouseCodeRotationStateEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WarehouseCodeRotationStateRepository extends JpaRepository<WarehouseCodeRotationStateEntity, Long> {

    Optional<WarehouseCodeRotationStateEntity> findByFactoryIgnoreCaseAndStationKeyAndMaterialCode(
            String factory, String stationKey, String materialCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from WarehouseCodeRotationStateEntity s
            where lower(s.factory) = lower(:factory)
              and s.stationKey = :stationKey
              and s.materialCode = :materialCode
            """)
    Optional<WarehouseCodeRotationStateEntity> findForUpdate(@Param("factory") String factory,
                                                               @Param("stationKey") String stationKey,
                                                               @Param("materialCode") String materialCode);
}
