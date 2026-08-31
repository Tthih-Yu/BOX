-- =====================================================
-- DataScope Migration 回滚脚本
-- 作者：AI Codex
-- 日期：2026-08-31
-- 警告：仅在测试环境使用，生产环境需要DBA审核
-- =====================================================

-- 回滚 V0.9.8 (日志和事件)
-- ALTER TABLE sys_outbox_event DROP COLUMN delivery_area;
-- ALTER TABLE sys_outbox_event DROP COLUMN factory;
-- ALTER TABLE sys_outbox_event DROP COLUMN scope_type;
-- DROP INDEX idx_outbox_scope ON sys_outbox_event;
-- ALTER TABLE log_scan DROP COLUMN delivery_area;
-- ALTER TABLE log_scan DROP COLUMN factory;
-- DROP INDEX idx_scan_scope ON log_scan;

-- 回滚 V0.9.7 (计划链)
-- DROP INDEX idx_purchase_factory ON t_purchase_requirement;
-- DROP INDEX idx_demand_factory ON t_material_demand;
-- DROP INDEX idx_plan_factory ON t_production_plan;
-- ALTER TABLE t_purchase_requirement DROP COLUMN factory;
-- ALTER TABLE t_material_demand DROP COLUMN factory;
-- ALTER TABLE t_production_plan DROP COLUMN factory;

-- 回滚 V0.9.6 (PrintJob)
-- DROP INDEX idx_print_scope ON t_print_job;
-- ALTER TABLE t_print_job DROP COLUMN delivery_area;

-- 回滚 V0.9.5 (Box & Label)
-- DROP INDEX idx_label_scope ON t_label;
-- DROP INDEX idx_box_scope ON t_box;
-- ALTER TABLE t_label DROP COLUMN delivery_area;
-- ALTER TABLE t_label DROP COLUMN factory;
-- ALTER TABLE t_box DROP COLUMN delivery_area;
-- ALTER TABLE t_box DROP COLUMN factory;

-- 回滚 V0.9.4 (Inventory)
-- DROP INDEX idx_inventory_scope ON t_inventory;
-- ALTER TABLE t_inventory DROP COLUMN delivery_area;
-- ALTER TABLE t_inventory DROP COLUMN factory;

-- 回滚 V0.9.3 (用户范围)
-- ALTER TABLE sys_user DROP COLUMN version;
-- DELETE FROM sys_delivery_area WHERE factory_code = '弋江';
-- DELETE FROM sys_factory WHERE factory_code = '弋江';
-- DROP TABLE IF EXISTS sys_delivery_area;
-- DROP TABLE IF EXISTS sys_factory;
-- DROP TABLE IF EXISTS sys_user_delivery_area;
-- ALTER TABLE sys_user DROP COLUMN factory;

-- =====================================================
-- 说明：
-- 1. 默认全部注释，防止误执行
-- 2. Expand Schema 设计为向后兼容，通常只需回滚应用，保留 Schema
-- 3. 只有确认需要完全回滚时才执行这些 SQL
-- 4. 执行前必须备份数据库
-- =====================================================
