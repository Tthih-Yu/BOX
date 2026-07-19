package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_import_batch")
public class ImportBatchEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String batchNo;
    private String importType;
    private String fileName;
    private Integer totalRows = 0;
    private Integer successRows = 0;
    private Integer failedRows = 0;
    @Enumerated(EnumType.STRING) private com.example.materialpull.enums.ImportStatus status = com.example.materialpull.enums.ImportStatus.RUNNING;
    private String operator;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @PrePersist public void prePersist(){ startedAt = LocalDateTime.now(); }
}
