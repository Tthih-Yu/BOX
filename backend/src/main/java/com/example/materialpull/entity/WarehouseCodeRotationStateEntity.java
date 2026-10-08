package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工厂 + 工位 + 物料维度的仓库代号轮换状态。
 *
 * 该表只保存已经随补货任务一起成功提交的数据；预览、重复拦截和回滚事务都不会推进状态。
 */
@Data
@Entity
@Table(name = "t_warehouse_code_rotation_state", indexes = {
        @Index(name = "uk_warehouse_rotation_key", columnList = "factory,stationKey,materialCode", unique = true),
        @Index(name = "idx_warehouse_rotation_updated", columnList = "updatedAt")
})
public class WarehouseCodeRotationStateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, length = 64)
    private String factory;

    /** 归一化后的总装地址，用于唯一键。 */
    @Column(nullable = false, length = 255)
    private String stationKey;

    /** 原始总装地址快照，仅用于排查和日志。 */
    @Column(nullable = false, length = 255)
    private String stationAddress;

    @Column(nullable = false, length = 128)
    private String materialCode;

    @Column(nullable = false, length = 128)
    private String lastWarehouseCode;

    @Column(length = 128)
    private String lastTaskNo;

    @Column(nullable = false)
    private Long sequenceNo = 0L;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
        normalize();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        normalize();
    }

    private void normalize() {
        if (factory != null) factory = factory.trim();
        if (stationKey != null) stationKey = stationKey.trim();
        if (stationAddress != null) stationAddress = stationAddress.trim();
        if (materialCode != null) materialCode = materialCode.trim();
        if (lastWarehouseCode != null) lastWarehouseCode = lastWarehouseCode.trim();
        if (lastTaskNo != null) lastTaskNo = lastTaskNo.trim();
        if (sequenceNo == null || sequenceNo < 0) sequenceNo = 0L;
    }
}
