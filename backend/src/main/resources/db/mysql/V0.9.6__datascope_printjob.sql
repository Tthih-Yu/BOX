-- =====================================================
-- DataScope Migration V0.9.6
-- 功能：业务对象范围字段 - PrintJob
-- 作者：AI Codex
-- 日期：2026-08-31
-- 说明：Expand Schema，向后兼容
-- =====================================================

-- 1. PrintJob 增加 deliveryArea 字段（factory 已存在）
ALTER TABLE t_print_job
ADD COLUMN delivery_area VARCHAR(64) NULL COMMENT '配送区域快照（从Task继承）';

-- 2. 增加索引
CREATE INDEX idx_print_scope ON t_print_job(factory, delivery_area);

-- =====================================================
-- 说明：
-- 1. factory 字段已在 V0.9.2 中添加
-- 2. 本次只增加 delivery_area
-- 3. 从 Task 继承完整的 factory + delivery_area
-- =====================================================
