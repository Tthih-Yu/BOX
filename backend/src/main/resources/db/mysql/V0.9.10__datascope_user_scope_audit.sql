-- 账号安全属性审计；不保存密码或密码哈希。
CREATE TABLE log_user_scope (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    action VARCHAR(24) NOT NULL,
    target_user_id BIGINT NULL,
    target_username VARCHAR(64) NOT NULL,
    operator_user_id BIGINT NULL,
    operator_username VARCHAR(64) NULL,
    old_role VARCHAR(32) NULL,
    new_role VARCHAR(32) NULL,
    old_factory VARCHAR(64) NULL,
    new_factory VARCHAR(64) NULL,
    old_delivery_areas VARCHAR(2000) NULL,
    new_delivery_areas VARCHAR(2000) NULL,
    old_enabled BOOLEAN NULL,
    new_enabled BOOLEAN NULL,
    reason VARCHAR(500) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_scope_audit_target_created (target_user_id, created_at),
    INDEX idx_user_scope_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号范围安全属性审计';
