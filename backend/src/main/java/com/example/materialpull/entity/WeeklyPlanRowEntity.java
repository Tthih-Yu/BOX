package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_weekly_plan_row", indexes = {
        @Index(name = "idx_wpr_batch", columnList = "batchNo"),
        @Index(name = "idx_wpr_year_week_code", columnList = "planYear,weekNo,productCode"),
        @Index(name = "idx_wpr_bizkey", columnList = "planYear,weekNo,factory,project,customerNo,productCode,packageQty")
})
public class WeeklyPlanRowEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String batchNo;
    private Integer planYear;
    private Integer weekNo;
    private String customer;
    private String factory;
    private String project;
    private String customerNo;
    /** 8D号(8位纯数字)，与BOM关联的业务键。 */
    private String productCode;
    private String description;
    private BigDecimal packageQty;
    private BigDecimal jph;
    private BigDecimal wh;
    /** 本周七天数量之和(白+夜合并后)。 */
    private BigDecimal weekQty = BigDecimal.ZERO;
    /** 库内乐观锁版本，用于冲突覆盖判定；与 Excel 版本号无关。 */
    private Integer version = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
