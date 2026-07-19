package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_inventory", indexes = {
        @Index(name = "idx_inv_wh_material", columnList = "warehouseMaterialCode"),
        @Index(name = "idx_inv_location", columnList = "warehouseCode,locationCode")
})
public class InventoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String warehouseCode;
    private String locationCode;
    private String warehouseMaterialCode;
    private String materialCode;
    private String materialName;
    private BigDecimal stockQty = BigDecimal.ZERO;
    private BigDecimal lockedQty = BigDecimal.ZERO;
    private BigDecimal availableQty = BigDecimal.ZERO;
    private BigDecimal safetyStock = BigDecimal.ZERO;
    private String batchNo;
    private Boolean frozen = false;
    private String freezeReason;
    private LocalDateTime lastCheckedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); recalc(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); recalc(); }
    public void recalc(){ if(stockQty == null) stockQty = BigDecimal.ZERO; if(lockedQty == null) lockedQty = BigDecimal.ZERO; availableQty = stockQty.subtract(lockedQty); }
}
