package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_idempotency", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "requestKey", unique = true),
        @Index(name = "idx_idempotency_expire", columnList = "expiresAt")
})
public class IdempotencyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String requestKey;
    @Column(length = 80)
    private String requestHash;
    private String businessType;
    private String businessNo;
    private String status;
    @Column(length = 2000) private String message;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}
