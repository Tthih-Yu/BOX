package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "m_material_mapping", indexes = {
        @Index(name = "idx_mapping_line_material", columnList = "lineMaterialCode"),
        @Index(name = "idx_mapping_material_station", columnList = "lineMaterialCode,stationCode"),
        @Index(name = "idx_mapping_material_order", columnList = "lineMaterialCode,mappingOrder"),
        @Index(name = "idx_mapping_warehouse_code", columnList = "warehouseCode"),
        @Index(name = "idx_mapping_warehouse_material", columnList = "warehouseMaterialCode")
})
public class MaterialMappingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 现场二维码扫出的物料号，对应需求表 B 列。 */
    private String lineMaterialCode;
    /**
     * 工位/供料点地址。来自工位二维码扫出的工位信息。
     * 用于区分同一物料喂给多个工位时各自的仓库代号；同物料只有一个工位时可留空。
     */
    private String stationCode;
    /** 兼容旧字段：新逻辑中等同于 warehouseCode。 */
    private String warehouseMaterialCode;
    /** 仓库标签第一行条形码，对应需求表 C 列。 */
    private String warehouseCode;
    private String boxSize;
    private BigDecimal quantity = BigDecimal.ZERO;
    private Integer mappingOrder = 1;
    private String deliveryType = "NORMAL";
    private String description;
    private Boolean enabled = true;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); normalize(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); normalize(); }
    private void normalize() {
        lineMaterialCode = blankToNull(lineMaterialCode);
        stationCode = blankToNull(stationCode);
        warehouseCode = blankToNull(warehouseCode);
        warehouseMaterialCode = blankToNull(warehouseMaterialCode);
        if (warehouseCode == null) warehouseCode = warehouseMaterialCode;
        if (warehouseMaterialCode == null) warehouseMaterialCode = warehouseCode;
        boxSize = blankToNull(boxSize);
        deliveryType = blankToNull(deliveryType);
        if (deliveryType == null) deliveryType = "NORMAL";
        deliveryType = deliveryType.toUpperCase();
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (mappingOrder == null || mappingOrder <= 0) mappingOrder = 1;
        if (enabled == null) enabled = true;
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
