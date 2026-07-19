-- V0.8.4 生产落地升级脚本
-- 执行前请先备份数据库；MySQL 8.x 环境建议在停机窗口内执行。

ALTER TABLE m_material
    ADD COLUMN material_image_url VARCHAR(255) NULL;

ALTER TABLE t_label
    ADD COLUMN material_image_url VARCHAR(255) NULL;

ALTER TABLE t_replenishment_task
    ADD COLUMN material_image_url VARCHAR(255) NULL,
    ADD COLUMN delivery_agv_job_no VARCHAR(255) NULL,
    ADD COLUMN return_agv_job_no VARCHAR(255) NULL;

ALTER TABLE t_print_job
    ADD COLUMN external_job_no VARCHAR(255) NULL,
    ADD COLUMN response_payload VARCHAR(4000) NULL;

CREATE INDEX idx_task_delivery_agv_job ON t_replenishment_task(delivery_agv_job_no);
CREATE INDEX idx_task_return_agv_job ON t_replenishment_task(return_agv_job_no);
