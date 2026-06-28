package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "log_scan", indexes = {
        @Index(name = "idx_scan_label", columnList = "labelCode"),
        @Index(name = "idx_scan_time", columnList = "scanAt")
})
public class ScanLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String labelCode;
    private String boxCode;
    private String action;
    private Boolean success;
    private String message;
    private String operator;
    private String deviceNo;
    private String stationCode;
    private String materialCode;
    private LocalDateTime scanAt;
    @PrePersist public void prePersist(){ if(scanAt == null) scanAt = LocalDateTime.now(); }
}
