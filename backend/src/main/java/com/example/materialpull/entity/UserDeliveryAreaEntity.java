package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户配送区域关联表
 * 对应 Migration V0.9.3
 */
@Data
@Entity
@Table(name = "sys_user_delivery_area", indexes = {
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_delivery_area", columnList = "deliveryArea")
})
public class UserDeliveryAreaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String deliveryArea;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
