package com.example.materialpull.repository;

import com.example.materialpull.entity.UserScopeAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserScopeAuditLogRepository extends JpaRepository<UserScopeAuditLogEntity, Long> {
}
