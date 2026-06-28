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

    /** 物料 + 工位精确命中：同物料喂多个工位时，用工位地址锁定该工位的仓库代号(可能是 A/B 两条)。 */
    List<MaterialMappingEntity> findByLineMaterialCodeAndStationCodeAndEnabledTrueOrderByMappingOrderAscIdAsc(String lineMaterialCode, String stationCode);

    /** 该物料涉及的所有不同工位，用于判断"是否多工位"。 */
    @org.springframework.data.jpa.repository.Query("select distinct m.stationCode from MaterialMappingEntity m where m.lineMaterialCode = :materialCode and m.enabled = true")
    List<String> findDistinctStationCodesByMaterial(@org.springframework.data.repository.query.Param("materialCode") String materialCode);
}
