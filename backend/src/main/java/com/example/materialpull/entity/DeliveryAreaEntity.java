package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 配送区域字典表
 * 对应 Migration V0.9.3
 */
@Data
@Entity
@Table(name = "sys_delivery_area", indexes = {
        @Index(name = "idx_factory", columnList = "factoryCode")
})
public class DeliveryAreaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String factoryCode;

    @Column(nullable = false, length = 64)
    private String areaCode;

    @Column(length = 128)
    private String areaName;

    @Column(nullable = false)
    private Boolean enabled = true;

    private Integer displayOrder = 0;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = updatedAt = LocalDateTime.now();
        normalize();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        normalize();
    }

    private void normalize() {
        if (factoryCode != null) {
            factoryCode = factoryCode.trim();
        }
        if (areaCode != null) {
            areaCode = areaCode.trim();
        }
        if (areaName != null) {
            areaName = areaName.trim();
        }
        if (enabled == null) {
            enabled = true;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }
}
