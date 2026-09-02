-- 日志按时间范围分页、流式导出的查询索引。
-- 生产 MySQL 使用 ddl-auto=validate，执行本脚本后再发布包含日志分页功能的后端。
-- 执行前请备份数据库；每条 CREATE INDEX 仅应执行一次。

CREATE INDEX idx_scan_time_id ON log_scan(scan_at, id);
CREATE INDEX idx_scan_label_time_id ON log_scan(label_code, scan_at, id);
CREATE INDEX idx_scan_scope_time_id ON log_scan(factory, delivery_area, scan_at, id);

CREATE INDEX idx_task_log_created_id ON log_task(created_at, id);
CREATE INDEX idx_print_log_created_id ON log_print(created_at, id);
CREATE INDEX idx_interface_log_created_id ON log_interface(created_at, id);
