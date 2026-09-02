package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "log_scan", indexes = {
        @Index(name = "idx_scan_label", columnList = "labelCode"),
        @Index(name = "idx_scan_time", columnList = "scanAt"),
        @Index(name = "idx_scan_scope", columnList = "factory,deliveryArea"),
        @Index(name = "idx_scan_time_id", columnList = "scanAt,id"),
        @Index(name = "idx_scan_label_time_id", columnList = "labelCode,scanAt,id"),
        @Index(name = "idx_scan_scope_time_id", columnList = "factory,deliveryArea,scanAt,id")
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
    private String factory;
    private String deliveryArea;
    private LocalDateTime scanAt;
    @PrePersist public void prePersist(){ if(scanAt == null) scanAt = LocalDateTime.now(); }
}
