package com.example.materialpull.entity;

import com.example.materialpull.enums.WeeklyPlanStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_weekly_plan_batch", indexes = {
        @Index(name = "idx_wp_batch_year_week", columnList = "planYear,weekNo")
})
public class WeeklyPlanBatchEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String batchNo;
    private Integer planYear;
    private Integer weekNo;
    private String excelVersion;
    private String titleText;
    private String factory;
    private String fileName;
    @Enumerated(EnumType.STRING) private WeeklyPlanStatus status = WeeklyPlanStatus.RUNNING;
    private Integer totalRows = 0;
    private Integer successRows = 0;
    private Integer failedRows = 0;
    private String operator;
    private String remark;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @PrePersist public void prePersist(){ if (startedAt == null) startedAt = LocalDateTime.now(); }
}
