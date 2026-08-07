-- 安卓APP两阶段扫码：给补货任务增加“本次申请单位”。不做单位换算，不回写基础数据。
-- 生产 MySQL 使用 ddl-auto=validate，必须先执行本 SQL 再启动新版后端。执行前请先备份。

ALTER TABLE t_replenishment_task
    ADD COLUMN request_unit VARCHAR(32) NOT NULL DEFAULT '个'
    COMMENT '本次任务申请单位；不做单位换算';

UPDATE t_replenishment_task
SET request_unit = '个'
WHERE request_unit IS NULL OR TRIM(request_unit) = '';
