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

-- =====================================================
-- 向后兼容性说明：
-- 1. 字段为 NULL - 旧代码不受影响
-- 2. 新创建的 Inventory 会保存范围快照
-- 3. 历史数据范围回填在后续批次执行
-- =====================================================
