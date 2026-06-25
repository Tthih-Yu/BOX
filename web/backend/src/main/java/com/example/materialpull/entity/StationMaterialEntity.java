package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "m_station_material", indexes = {
        @Index(name = "idx_station_material", columnList = "stationCode,materialCode"),
        @Index(name = "idx_station_delivery", columnList = "projectCode,routeName,deliveryAddress"),
        @Index(name = "idx_station_bundle", columnList = "lineCode,bundleCode"),
        @Index(name = "idx_station_process", columnList = "processCode,stationCode")
})
public class StationMaterialEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String lineCode;
    private String stationCode;
    private String stationName;
    private String processCode;
    private String processName;
    private String bundleCode;
    private String projectCode;
    private String routeName;
    private String deliveryAddress;
    private String areaCode;
    private String rackCode;
    private String shelfCode;
    private String warehouseLocation;
    private String materialCode;
    private String materialName;
    private String warehouseMaterialCode;
    private String unit;
    private String specText;
    private BigDecimal standardBoxQty = BigDecimal.ZERO;
    private BigDecimal dailyUsage = BigDecimal.ZERO;
    private BigDecimal triggerQty = BigDecimal.ZERO;
    private BigDecimal singleBoxQty = BigDecimal.ZERO;
    private BigDecimal safetyStock = BigDecimal.ZERO;
    private BigDecimal minStock = BigDecimal.ZERO;
    private BigDecimal maxStock = BigDecimal.ZERO;
    private BigDecimal mpcThresholdQty = BigDecimal.ZERO;
    private Integer productionCycleMinutes = 0;
    private Integer deliveryCycleMinutes = 0;
    private Boolean forecastEnabled = true;
    private Boolean enabled = true;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); normalize(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); normalize(); }
    private void normalize(){
        if (standardBoxQty == null) standardBoxQty = BigDecimal.ZERO;
        if (dailyUsage == null) dailyUsage = BigDecimal.ZERO;
        if (triggerQty == null) triggerQty = BigDecimal.ZERO;
        if (singleBoxQty == null) singleBoxQty = BigDecimal.ZERO;
        if (safetyStock == null) safetyStock = BigDecimal.ZERO;
        if (minStock == null) minStock = BigDecimal.ZERO;
        if (maxStock == null) maxStock = BigDecimal.ZERO;
        if (mpcThresholdQty == null) mpcThresholdQty = BigDecimal.ZERO;
        if (productionCycleMinutes == null) productionCycleMinutes = 0;
        if (deliveryCycleMinutes == null) deliveryCycleMinutes = 0;
        if (forecastEnabled == null) forecastEnabled = true;
        if (enabled == null) enabled = true;
    }
}
