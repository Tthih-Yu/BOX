-- DataScope 历史回填控制表（仅供隔离验证库演练，禁止直接在生产执行）
-- 控制表把候选、原值、新值、依据、审批人与批次固化，业务表更新脚本只读取 APPROVED 记录。

CREATE TABLE IF NOT EXISTS datascope_backfill_batch (
    batch_no VARCHAR(64) NOT NULL PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    description VARCHAR(500) NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_by VARCHAR(64) NULL,
    approved_at DATETIME NULL,
    applied_at DATETIME NULL,
    recovered_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS datascope_backfill_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(64) NOT NULL,
    object_type VARCHAR(32) NOT NULL,
    record_id BIGINT NOT NULL,
    business_no VARCHAR(128) NULL,
    old_factory VARCHAR(64) NULL,
    old_delivery_area VARCHAR(64) NULL,
    new_factory VARCHAR(64) NULL,
    new_delivery_area VARCHAR(64) NULL,
    evidence VARCHAR(1000) NOT NULL,
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(64) NULL,
    reviewed_at DATETIME NULL,
    applied_at DATETIME NULL,
    recovered_at DATETIME NULL,
    apply_note VARCHAR(500) NULL,
    UNIQUE KEY uk_datascope_candidate (batch_no, object_type, record_id),
    KEY idx_datascope_candidate_review (batch_no, review_status),
    CONSTRAINT fk_datascope_candidate_batch FOREIGN KEY (batch_no)
        REFERENCES datascope_backfill_batch(batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
