package com.example.materialpull.repository;

import com.example.materialpull.entity.StationMaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StationMaterialRepository extends JpaRepository<StationMaterialEntity, Long> {
    Optional<StationMaterialEntity> findByStationCodeAndMaterialCodeAndEnabledTrue(String stationCode, String materialCode);
    Optional<StationMaterialEntity> findFirstByMaterialCodeAndEnabledTrue(String materialCode);
    List<StationMaterialEntity> findByLineCodeAndStationCodeAndEnabledTrue(String lineCode, String stationCode);
    List<StationMaterialEntity> findByLineCodeAndEnabledTrue(String lineCode);
}
