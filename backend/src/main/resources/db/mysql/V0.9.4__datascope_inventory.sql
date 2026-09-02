-- =====================================================
-- DataScope Migration V0.9.4
-- 功能：业务对象范围字段 - Inventory
-- 作者：AI Codex
-- 日期：2026-08-31
-- 说明：Expand Schema，向后兼容
-- =====================================================

-- 1. Inventory 增加范围字段
ALTER TABLE t_inventory
ADD COLUMN factory VARCHAR(32) NULL COMMENT '所属工厂快照',
ADD COLUMN delivery_area VARCHAR(64) NULL COMMENT '配送区域快照';

-- 2. 增加索引
CREATE INDEX idx_inventory_scope ON t_inventory(factory, delivery_area);

-- 3. 新范围内的库存业务唯一维度。三个历史业务键均为 VARCHAR(255)，直接复合索引会在 utf8mb4
--    下超过 InnoDB 3072 字节上限；用完整字段 SHA-256 生成列保证唯一性且不截断业务数据。
--    任一键为 NULL 时生成 NULL，保持 MySQL 复合唯一索引对历史 NULL 数据“可并存”的兼容语义。
ALTER TABLE t_inventory
ADD COLUMN scope_location_material_hash BINARY(32)
    GENERATED ALWAYS AS (
        CASE
            WHEN factory IS NULL OR delivery_area IS NULL
              OR warehouse_code IS NULL OR location_code IS NULL OR warehouse_material_code IS NULL
            THEN NULL
            ELSE UNHEX(SHA2(CONCAT_WS(CHAR(31), factory, delivery_area,
                                      warehouse_code, location_code, warehouse_material_code), 256))
        END
    ) STORED;

CREATE UNIQUE INDEX uk_inventory_scope_location_material
ON t_inventory(scope_location_material_hash);

-- =====================================================
-- 向后兼容性说明：
-- 1. 字段为 NULL - 旧代码不受影响
-- 2. 新创建的 Inventory 会保存范围快照
-- 3. 历史数据范围回填在后续批次执行
-- =====================================================
