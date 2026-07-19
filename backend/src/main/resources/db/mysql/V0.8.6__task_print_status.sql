-- V0.8.6 打印状态回写到仓库补货任务
-- 用于将本地打印代理领取、打印成功/失败状态同步展示在“仓库补货任务”列表。

ALTER TABLE t_replenishment_task
    ADD COLUMN print_status VARCHAR(32) NULL COMMENT '打印作业状态：RENDERED/SENT/PRINTED/FAILED/CANCELLED',
    ADD COLUMN print_channel VARCHAR(16) NULL COMMENT '打印方式：BROWSER=浏览器打印，AGENT=本地代理自动打印',
    ADD COLUMN printed_at DATETIME NULL COMMENT '打印成功时间',
    ADD COLUMN print_last_error VARCHAR(255) NULL COMMENT '最近一次打印错误';

ALTER TABLE t_print_job
    ADD COLUMN print_channel VARCHAR(16) NULL COMMENT '打印方式：BROWSER=浏览器打印，AGENT=本地代理自动打印';
