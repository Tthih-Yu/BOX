-- 全局管理员的物料用量看板仅按 created_at 查询；已有复合索引均以 factory 开头，无法高效支持此查询。
CREATE INDEX idx_task_created_at
ON t_replenishment_task(created_at);
