-- 按“工厂 + 总装地址 + 物料号”持久化仓库代号轮换状态。
-- 正式建单事务锁定映射行和本状态行，确保多设备、多实例并发时仍按确定顺序轮换。
CREATE TABLE t_warehouse_code_rotation_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NULL,
    factory VARCHAR(64) NOT NULL,
    station_key VARCHAR(255) NOT NULL,
    station_address VARCHAR(255) NOT NULL,
    material_code VARCHAR(128) NOT NULL,
    last_warehouse_code VARCHAR(128) NOT NULL,
    last_task_no VARCHAR(128) NULL,
    sequence_no BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_rotation_key (factory, station_key, material_code),
    KEY idx_warehouse_rotation_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='工位物料仓库代号轮换状态';
