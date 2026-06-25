package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "m_material", indexes = {
        @Index(name = "idx_material_code", columnList = "materialCode", unique = true),
        @Index(name = "idx_warehouse_code", columnList = "warehouseMaterialCode")
})
public class MaterialEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String materialCode;
    private String warehouseMaterialCode;
    @Column(nullable = false) private String materialName;
    private String spec;
    private String unit;
    private String category;
    private String supplier;
    private String materialImageUrl;
    private BigDecimal safetyStock = BigDecimal.ZERO;
    private BigDecimal minPackageQty = BigDecimal.ZERO;
    private Boolean enabled = true;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
