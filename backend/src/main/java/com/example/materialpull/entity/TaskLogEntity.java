package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "log_task", indexes = {
        @Index(name = "idx_task_log_created_id", columnList = "createdAt,id")
})
public class TaskLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String taskNo;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String operator;
    private String message;
    private LocalDateTime createdAt;
    @PrePersist public void prePersist(){ createdAt = LocalDateTime.now(); }
}
