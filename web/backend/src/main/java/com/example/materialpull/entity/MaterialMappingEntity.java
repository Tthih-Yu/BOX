package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "m_material_mapping", indexes = {
        @Index(name = "idx_mapping_line_material", columnList = "lineMaterialCode"),
        @Index(name = "idx_mapping_warehouse_material", columnList = "warehouseMaterialCode")
})
public class MaterialMappingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String lineMaterialCode;
    private String warehouseMaterialCode;
    private String description;
    private Boolean enabled = true;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
