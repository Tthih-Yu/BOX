package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "log_print", indexes = {
        @Index(name = "idx_print_log_created_id", columnList = "createdAt,id")
})
public class PrintLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String labelCode;
    private String action;
    private String operator;
    private String printerName;
    private Boolean success;
    private String message;
    private LocalDateTime createdAt;
    @PrePersist public void prePersist(){ createdAt = LocalDateTime.now(); }
}
