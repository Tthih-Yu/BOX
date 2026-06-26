package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "m_material_mapping", indexes = {
        @Index(name = "idx_mapping_line_material", columnList = "lineMaterialCode"),
        @Index(name = "idx_mapping_warehouse_material", columnList = "warehouseMaterialCode"),
        @Index(name = "idx_mapping_warehouse_code", columnList = "warehouseCode"),
        @Index(name = "idx_mapping_usage", columnList = "lineMaterialCode,labelUsageType,enabled")
})
public class MaterialMappingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 现场标签二维码中的物料号。 */
    private String lineMaterialCode;
    /** 仓库系统使用的料号；没有单独仓库料号时等同于现场物料号。 */
    private String warehouseMaterialCode;
    /** 仓库打印标签上的条形码内容，对应 Excel 的“仓库代号”。 */
    private String warehouseCode;
    /** 盒子大小，对应 Excel 的“盒子大小”。 */
    private String boxSize;
    /** 单次配送数量，对应 Excel 的“数量”。 */
    private BigDecimal standardQty = BigDecimal.ZERO;
    /** USE=正常配送，SPARE=紧急配送。 */
    private String labelUsageType = "USE";
    /** NORMAL=正常配送，URGENT=紧急配送。 */
    private String deliveryMode = "NORMAL";
    private String description;
    private Boolean enabled = true;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); normalize(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); normalize(); }

    private void normalize() {
        lineMaterialCode = blankToNull(lineMaterialCode);
        warehouseMaterialCode = blankToNull(warehouseMaterialCode);
        warehouseCode = blankToNull(warehouseCode);
        boxSize = blankToNull(boxSize);
        labelUsageType = blankToNull(labelUsageType);
        deliveryMode = blankToNull(deliveryMode);
        if (warehouseMaterialCode == null) warehouseMaterialCode = lineMaterialCode;
        if (labelUsageType == null) labelUsageType = "USE";
        if (deliveryMode == null) deliveryMode = "SPARE".equalsIgnoreCase(labelUsageType) ? "URGENT" : "NORMAL";
        if (standardQty == null) standardQty = BigDecimal.ZERO;
        if (enabled == null) enabled = true;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
