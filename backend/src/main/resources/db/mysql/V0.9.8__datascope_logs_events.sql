-- =====================================================
-- DataScope Migration V0.9.8
-- 功能：日志和事件范围字段
-- 作者：AI Codex
-- 日期：2026-08-31
-- 说明：Expand Schema，向后兼容
-- =====================================================

-- 1. ScanLog 增加范围字段
ALTER TABLE log_scan
ADD COLUMN factory VARCHAR(32) NULL COMMENT '所属工厂快照',
ADD COLUMN delivery_area VARCHAR(64) NULL COMMENT '配送区域快照';

-- 2. OutboxEvent 增加范围字段
ALTER TABLE sys_outbox_event
ADD COLUMN scope_type VARCHAR(32) NULL COMMENT '范围类型: FACTORY_AREA/FACTORY_ONLY/GLOBAL',
ADD COLUMN factory VARCHAR(32) NULL COMMENT '所属工厂',
ADD COLUMN delivery_area VARCHAR(64) NULL COMMENT '配送区域（FACTORY_AREA时使用）';

-- 3. 增加索引
CREATE INDEX idx_scan_scope ON log_scan(factory, delivery_area);
CREATE INDEX idx_outbox_scope ON sys_outbox_event(scope_type, factory, delivery_area);

-- =====================================================
-- 说明：
-- 1. ScanLog 保存快照，避免日志成为权限旁路
-- 2. Outbox 携带原业务对象的范围，用于推送过滤
-- 3. scope_type 用于区分不同类型事件的推送策略
-- =====================================================
