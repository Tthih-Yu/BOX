package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_material_bom", indexes = {
        @Index(name = "idx_bom_product", columnList = "productCode"),
        @Index(name = "idx_bom_component", columnList = "componentMaterialCode"),
        @Index(name = "idx_bom_station", columnList = "lineCode,stationCode"),
        @Index(name = "idx_bom_bundle", columnList = "productCode,bundleCode")
})
public class MaterialBomEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String bomNo;
    private String bomVersion;
    private String productCode;
    private String productName;
    private String processCode;
    private String processName;
    private String bundleCode;
    private String lineCode;
    private String stationCode;
    private String stationName;
    private String rackCode;
    private String shelfCode;
    private String componentMaterialCode;
    private String componentMaterialName;
    private String warehouseMaterialCode;
    private BigDecimal usageQty = BigDecimal.ZERO;
    private BigDecimal loadPerBoxQty = BigDecimal.ZERO;
    private BigDecimal safetyStock = BigDecimal.ZERO;
    private BigDecimal minStock = BigDecimal.ZERO;
    private BigDecimal maxStock = BigDecimal.ZERO;
    private Integer productionCycleMinutes = 0;
    private Integer deliveryCycleMinutes = 0;
    private String unit;
    private Boolean enabled = true;
    private String sourceSystem = "SAP";
    @Column(length = 4000) private String rawPayload;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); normalize(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); normalize(); }
    private void normalize(){
        if (usageQty == null) usageQty = BigDecimal.ZERO;
        if (loadPerBoxQty == null) loadPerBoxQty = BigDecimal.ZERO;
        if (safetyStock == null) safetyStock = BigDecimal.ZERO;
        if (minStock == null) minStock = BigDecimal.ZERO;
        if (maxStock == null) maxStock = BigDecimal.ZERO;
        if (productionCycleMinutes == null) productionCycleMinutes = 0;
        if (deliveryCycleMinutes == null) deliveryCycleMinutes = 0;
        if (enabled == null) enabled = true;
    }
}
