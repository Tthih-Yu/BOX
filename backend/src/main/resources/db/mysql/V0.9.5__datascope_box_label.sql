-- =====================================================
-- DataScope Migration V0.9.5
-- 功能：业务对象范围字段 - Box & Label
-- 作者：AI Codex
-- 日期：2026-08-31
-- 说明：Expand Schema，向后兼容
-- =====================================================

-- 1. Box 增加范围字段（独立于 areaCode）
ALTER TABLE t_box
ADD COLUMN factory VARCHAR(32) NULL COMMENT '所属工厂快照',
ADD COLUMN delivery_area VARCHAR(64) NULL COMMENT '配送区域快照（独立于areaCode）';

-- 2. Label 增加范围字段（独立于 areaCode）
ALTER TABLE t_label
ADD COLUMN factory VARCHAR(32) NULL COMMENT '所属工厂快照',
ADD COLUMN delivery_area VARCHAR(64) NULL COMMENT '配送区域快照（独立于areaCode）';

-- 3. 增加索引
CREATE INDEX idx_box_scope ON t_box(factory, delivery_area);
CREATE INDEX idx_label_scope ON t_label(factory, delivery_area);

-- =====================================================
-- 重要说明：
-- 1. areaCode 字段保留不变，用于其他业务逻辑
-- 2. factory + delivery_area 专门用于 DataScope 权限控制
-- 3. 两者语义不同，不要混淆
-- =====================================================
