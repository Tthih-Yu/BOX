package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 账号角色、工厂、配送区域和启停状态的不可逆审计记录；不保存密码或密码哈希。 */
@Data
@Entity
@Table(name = "log_user_scope", indexes = {
        @Index(name = "idx_user_scope_audit_target_created", columnList = "targetUserId,createdAt"),
        @Index(name = "idx_user_scope_audit_created", columnList = "createdAt")
})
public class UserScopeAuditLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 24) private String action;
    private Long targetUserId;
    @Column(nullable = false, length = 64) private String targetUsername;
    private Long operatorUserId;
    @Column(length = 64) private String operatorUsername;
    @Column(length = 32) private String oldRole;
    @Column(length = 32) private String newRole;
    @Column(length = 64) private String oldFactory;
    @Column(length = 64) private String newFactory;
    @Column(length = 2000) private String oldDeliveryAreas;
    @Column(length = 2000) private String newDeliveryAreas;
    private Boolean oldEnabled;
    private Boolean newEnabled;
    @Column(length = 500) private String reason;
    @Column(length = 64) private String traceId;
    @Column(nullable = false) private LocalDateTime createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
