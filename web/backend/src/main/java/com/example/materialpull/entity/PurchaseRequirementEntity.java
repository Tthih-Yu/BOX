package com.example.materialpull.entity;

import com.example.materialpull.enums.PurchaseStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_purchase_requirement", indexes = {
        @Index(name = "idx_purchase_no", columnList = "purchaseNo", unique = true),
        @Index(name = "idx_purchase_demand", columnList = "demandNo"),
        @Index(name = "idx_purchase_status", columnList = "status"),
        @Index(name = "idx_purchase_plan", columnList = "planNo"),
        @Index(name = "idx_purchase_whmat", columnList = "warehouseMaterialCode")
})
public class PurchaseRequirementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String purchaseNo;
    private String demandNo;
    private String planNo;
    private String materialCode;
    private String materialName;
    private String warehouseMaterialCode;
    private BigDecimal currentStock = BigDecimal.ZERO;
    private BigDecimal safetyStock = BigDecimal.ZERO;
    private BigDecimal forecastQty = BigDecimal.ZERO;
    private BigDecimal thresholdQty = BigDecimal.ZERO;
    private BigDecimal shortageQty = BigDecimal.ZERO;
    private BigDecimal requestQty = BigDecimal.ZERO;
    private String forecastSource;
    private String recommendedSupplier;
    private String mpcRemark;
    @Enumerated(EnumType.STRING) private PurchaseStatus status = PurchaseStatus.CREATED;
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); normalize(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); normalize(); }
    private void normalize(){
        if (currentStock == null) currentStock = BigDecimal.ZERO;
        if (safetyStock == null) safetyStock = BigDecimal.ZERO;
        if (forecastQty == null) forecastQty = BigDecimal.ZERO;
        if (thresholdQty == null) thresholdQty = BigDecimal.ZERO;
        if (shortageQty == null) shortageQty = BigDecimal.ZERO;
        if (requestQty == null) requestQty = BigDecimal.ZERO;
    }
}
