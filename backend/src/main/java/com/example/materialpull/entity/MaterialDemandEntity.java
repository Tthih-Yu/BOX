package com.example.materialpull.entity;

import com.example.materialpull.enums.DemandStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_material_demand", indexes = {
        @Index(name = "idx_demand_no", columnList = "demandNo", unique = true),
        @Index(name = "idx_demand_plan", columnList = "planNo"),
        @Index(name = "idx_demand_status", columnList = "status"),
        @Index(name = "idx_demand_whmat", columnList = "warehouseMaterialCode"),
        @Index(name = "idx_demand_station", columnList = "lineCode,stationCode"),
        @Index(name = "idx_demand_bundle", columnList = "bundleCode")
})
public class MaterialDemandEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String demandNo;
    private String planNo;
    private String sourceType = "PPC_PLAN";
    private String lineCode;
    private String stationCode;
    private String stationName;
    private String productCode;
    private String bundleCode;
    private String processCode;
    private String rackCode;
    private String shelfCode;
    private String materialCode;
    private String materialName;
    private String warehouseMaterialCode;
    private BigDecimal usageQty = BigDecimal.ZERO;
    private BigDecimal planQty = BigDecimal.ZERO;
    private BigDecimal demandQty = BigDecimal.ZERO;
    private BigDecimal boxQty = BigDecimal.ZERO;
    private BigDecimal singleBoxQty = BigDecimal.ZERO;
    private BigDecimal safetyStock = BigDecimal.ZERO;
    private BigDecimal inventoryAvailable = BigDecimal.ZERO;
    private BigDecimal shortageQty = BigDecimal.ZERO;
    private BigDecimal inventoryDifferenceQty = BigDecimal.ZERO;
    private BigDecimal adjustmentQty = BigDecimal.ZERO;
    private BigDecimal mpcThresholdQty = BigDecimal.ZERO;
    private Integer taskBoxCount = 0;
    private Integer productionCycleMinutes = 0;
    private Integer deliveryCycleMinutes = 0;
    private String taskNo;
    @Column(length = 2000) private String taskNos;
    @Enumerated(EnumType.STRING) private DemandStatus status = DemandStatus.OPEN;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); normalize(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); normalize(); }
    private void normalize(){
        if (usageQty == null) usageQty = BigDecimal.ZERO;
        if (planQty == null) planQty = BigDecimal.ZERO;
        if (demandQty == null) demandQty = BigDecimal.ZERO;
        if (boxQty == null) boxQty = BigDecimal.ZERO;
        if (singleBoxQty == null) singleBoxQty = BigDecimal.ZERO;
        if (safetyStock == null) safetyStock = BigDecimal.ZERO;
        if (inventoryAvailable == null) inventoryAvailable = BigDecimal.ZERO;
        if (shortageQty == null) shortageQty = BigDecimal.ZERO;
        if (inventoryDifferenceQty == null) inventoryDifferenceQty = BigDecimal.ZERO;
        if (adjustmentQty == null) adjustmentQty = BigDecimal.ZERO;
        if (mpcThresholdQty == null) mpcThresholdQty = BigDecimal.ZERO;
        if (taskBoxCount == null) taskBoxCount = 0;
        if (productionCycleMinutes == null) productionCycleMinutes = 0;
        if (deliveryCycleMinutes == null) deliveryCycleMinutes = 0;
    }
}
