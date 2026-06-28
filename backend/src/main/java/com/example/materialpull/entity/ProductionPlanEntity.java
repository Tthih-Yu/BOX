package com.example.materialpull.entity;

import com.example.materialpull.enums.PlanStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_production_plan", indexes = {
        @Index(name = "idx_plan_no", columnList = "planNo", unique = true),
        @Index(name = "idx_plan_status", columnList = "status"),
        @Index(name = "idx_plan_line", columnList = "lineCode,stationCode")
})
public class ProductionPlanEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String planNo;
    private String sourceSystem = "PPC";
    private String productCode;
    private String productName;
    private String lineCode;
    private String stationCode;
    private String bundleCode;
    private BigDecimal planQty = BigDecimal.ZERO;
    private BigDecimal bundleQty = BigDecimal.ZERO;
    private BigDecimal defaultBoxQty = BigDecimal.ZERO;
    private LocalDateTime planStartAt;
    private LocalDateTime dueAt;
    @Enumerated(EnumType.STRING) private PlanStatus status = PlanStatus.DRAFT;
    private String operator;
    private String remark;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
