package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_config", indexes = {
        @Index(name = "idx_config_key", columnList = "configKey", unique = true)
})
public class SystemConfigEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String configKey;
    @Column(length = 2000) private String configValue;
    private String configName;
    private String remark;
    private Boolean editable = true;
    private LocalDateTime updatedAt;
    @PrePersist @PreUpdate public void touch(){ if(configKey != null) configKey = configKey.trim(); updatedAt = LocalDateTime.now(); if(editable == null) editable = true; }
}
