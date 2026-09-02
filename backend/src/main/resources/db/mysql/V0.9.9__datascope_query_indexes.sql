-- =====================================================
-- DataScope Migration V0.9.9
-- 功能：范围查询索引
-- 说明：基于隔离 MySQL EXPLAIN；不修改已发布的前置 Migration。
-- =====================================================

-- Task 列表、状态筛选、日期筛选与待打印范围查询。
CREATE INDEX idx_task_scope_created
ON t_replenishment_task(factory, delivery_area, created_at);

CREATE INDEX idx_task_scope_status_created
ON t_replenishment_task(factory, delivery_area, status, created_at);

-- PrintJob 范围分页查询；factory/delivery_area 均为等值或 IN 条件，created_at 用于倒序分页。
CREATE INDEX idx_print_scope_created
ON t_print_job(factory, delivery_area, created_at);
