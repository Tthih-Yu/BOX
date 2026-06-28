package com.example.materialpull.entity;

import com.example.materialpull.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user", indexes = {
        @Index(name = "idx_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_role", columnList = "role")
})
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    private String realName;

    /**
     * 兼容旧数据库列名 password，但字段内容已改为 BCrypt Hash，不再返回给前端。
     */
    @JsonIgnore
    @Column(name = "password", nullable = false, length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role = UserRole.VIEWER;

    private String phone;
    private Boolean enabled = true;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordUpdatedAt;
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
        if (username != null) username = username.trim();
        if (realName != null) realName = realName.trim();
        if (phone != null) phone = phone.trim();
        if (enabled == null) enabled = true;
        if (role == null) role = UserRole.VIEWER;
    }
}
