package com.example.materialpull.repository;

import com.example.materialpull.entity.MaterialBomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MaterialBomRepository extends JpaRepository<MaterialBomEntity, Long> {
    Optional<MaterialBomEntity> findByBomNo(String bomNo);
    List<MaterialBomEntity> findByProductCodeAndEnabledTrue(String productCode);
    List<MaterialBomEntity> findByLineCodeAndStationCodeAndEnabledTrue(String lineCode, String stationCode);
}
