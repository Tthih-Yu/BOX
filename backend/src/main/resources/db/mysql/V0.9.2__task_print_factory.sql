ALTER TABLE t_replenishment_task
    ADD COLUMN factory VARCHAR(64) NULL COMMENT '任务创建时固化的所属工厂' AFTER task_no,
    ADD INDEX idx_task_factory_created (factory, created_at);

ALTER TABLE t_print_job
    ADD COLUMN factory VARCHAR(64) NULL COMMENT '从任务继承的所属工厂' AFTER print_job_no,
    ADD INDEX idx_print_factory_created (factory, created_at);
