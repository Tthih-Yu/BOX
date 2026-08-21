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
    /** 所属工厂。由员工在“料号映射”页手动维护，允许为空。 */
    private String factory;
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
    /**
     * 单根用量：一件产品消耗该组件的数量。由周计划自动计算引擎读取，
     * 组件需求量 = 计划数量 × 单根用量。允许为空，空表示尚未维护。
     */
    private BigDecimal singleUnitUsage;
    private Integer mappingOrder = 1;
    private String deliveryType = "NORMAL";
    /** 仓库货架位置，例如 C-26-D-5。直接透传到仓库标签和任务的 warehouseLocation 字段。 */
    private String warehouseLocation;
    /** 总装送达地址（工位地址），例如 盲栓台-01-A02。直接透传到任务的 deliveryAddress/sendStationAddress 字段。 */
    private String deliveryAddress;
    /** 配送区域。默认 1，用户可在“料号映射”页手动修改；用于仓库任务排序与定时按区域打标签。 */
    private String deliveryArea = "1";
    private String description;
    private Boolean enabled = true;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); normalize(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); normalize(); }
    private void normalize() {
        lineMaterialCode = blankToNull(lineMaterialCode);
        factory = blankToNull(factory);
        stationCode = blankToNull(stationCode);
        warehouseCode = blankToNull(warehouseCode);
        warehouseMaterialCode = blankToNull(warehouseMaterialCode);
        if (warehouseCode == null) warehouseCode = warehouseMaterialCode;
        if (warehouseMaterialCode == null) warehouseMaterialCode = warehouseCode;
        boxSize = blankToNull(boxSize);
        deliveryType = blankToNull(deliveryType);
        if (deliveryType == null) deliveryType = "NORMAL";
        deliveryType = deliveryType.toUpperCase();
        warehouseLocation = blankToNull(warehouseLocation);
        deliveryAddress = blankToNull(deliveryAddress);
        deliveryArea = blankToNull(deliveryArea);
        if (deliveryArea == null) deliveryArea = "1";
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (mappingOrder == null || mappingOrder <= 0) mappingOrder = 1;
        if (enabled == null) enabled = true;
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
