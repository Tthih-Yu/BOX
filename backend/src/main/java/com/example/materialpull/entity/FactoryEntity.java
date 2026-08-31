package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 工厂字典表
 * 对应 Migration V0.9.3
 */
@Data
@Entity
@Table(name = "sys_factory")
public class FactoryEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String factoryCode;

    @Column(length = 128)
    private String factoryName;

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
        if (factoryName != null) {
            factoryName = factoryName.trim();
        }
        if (enabled == null) {
            enabled = true;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }
}
