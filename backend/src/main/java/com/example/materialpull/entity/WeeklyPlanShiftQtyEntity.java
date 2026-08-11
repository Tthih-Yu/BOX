package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "t_weekly_plan_shift_qty", indexes = {
        @Index(name = "idx_wpsq_row", columnList = "rowId"),
        @Index(name = "idx_wpsq_batch", columnList = "batchNo")
})
public class WeeklyPlanShiftQtyEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 关联 t_weekly_plan_row.id。 */
    private Long rowId;
    /** 冗余批次号，便于按批清理。 */
    private String batchNo;
    /** 当天日期(补齐年份后)。 */
    private LocalDate planDate;
    /** DAY=白班 / NIGHT=夜班。 */
    private String shift;
    /** 当班数量，空按0。 */
    private BigDecimal qty = BigDecimal.ZERO;
}
