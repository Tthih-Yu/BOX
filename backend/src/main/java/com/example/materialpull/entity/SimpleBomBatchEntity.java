package com.example.materialpull.entity;

import com.example.materialpull.enums.SimpleBomBatchStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_simple_bom_batch", indexes = {
        @Index(name = "idx_bom_batch_status", columnList = "status")
})
public class SimpleBomBatchEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String batchNo;
    private String fileName;
    @Enumerated(EnumType.STRING) private SimpleBomBatchStatus status = SimpleBomBatchStatus.RUNNING;
    private Integer totalRows = 0;
    private Integer successRows = 0;
    private Integer failedRows = 0;
    private String operator;
    private String remark;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime activatedAt;
    @PrePersist public void prePersist(){ if (startedAt == null) startedAt = LocalDateTime.now(); }
}
