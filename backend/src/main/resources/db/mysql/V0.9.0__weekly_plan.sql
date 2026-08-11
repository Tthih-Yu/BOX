-- BOM 周计划联动 · 第三步：周计划导入与手工维护用表。
-- 设计要点：
--  * 每次上传一个周计划 = 一个批次(t_weekly_plan_batch)，记录标题解析出的年份/周次/Excel版本。
--  * 计划行(t_weekly_plan_row)按 8D 号等业务键存储，带库内 version 乐观锁用于冲突覆盖判定。
--  * 班次数量(t_weekly_plan_shift_qty)按“日期+班次”行存，跨月/跨年周不需要特殊建模；
--    当天数量 = 白班 + 夜班，周计划数量 = 该行七天数量之和。
-- 生产 MySQL 使用 ddl-auto=validate，必须先执行本 SQL 再启动新版后端。执行前请先备份。

CREATE TABLE IF NOT EXISTS t_weekly_plan_batch (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    batch_no      VARCHAR(64)  NOT NULL COMMENT '批次号，上传时生成',
    plan_year     INT          NOT NULL COMMENT '计划年份(日期只有月/日，年份由上传者选择或周次推导)',
    week_no       INT          NOT NULL COMMENT '周次，来自标题 WKxx',
    excel_version VARCHAR(32)           COMMENT '标题里的 Excel 版本号(如 V4)，仅展示与追溯',
    title_text    VARCHAR(255)          COMMENT '原始标题行文本',
    factory       VARCHAR(64)           COMMENT '生产工厂(便于按工厂区分多个计划员上传)',
    file_name     VARCHAR(255)          COMMENT '上传文件名',
    status        VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED',
    total_rows    INT          NOT NULL DEFAULT 0,
    success_rows  INT          NOT NULL DEFAULT 0,
    failed_rows   INT          NOT NULL DEFAULT 0,
    operator      VARCHAR(64)           COMMENT '上传计划员',
    remark        VARCHAR(500),
    started_at    DATETIME,
    finished_at   DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wp_batch_no (batch_no),
    KEY idx_wp_batch_year_week (plan_year, week_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='周计划导入批次';

CREATE TABLE IF NOT EXISTS t_weekly_plan_row (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    batch_no      VARCHAR(64)  NOT NULL COMMENT '所属上传批次',
    plan_year     INT          NOT NULL,
    week_no       INT          NOT NULL,
    customer      VARCHAR(64)           COMMENT '客户',
    factory       VARCHAR(64)           COMMENT '生产工厂',
    project       VARCHAR(64)           COMMENT '项目，允许空/占位值',
    customer_no   VARCHAR(64)           COMMENT '客户号，非纯数字，允许空',
    product_code  VARCHAR(32)  NOT NULL COMMENT '8D号(8位纯数字)，与BOM关联的业务键',
    description   VARCHAR(255)          COMMENT '描述',
    package_qty   DECIMAL(18,4)         COMMENT '标包',
    jph           DECIMAL(18,4)         COMMENT 'JPH，允许空',
    wh            DECIMAL(18,4)         COMMENT 'WH，允许空',
    week_qty      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '本周七天数量之和(白+夜合并后)',
    version       INT          NOT NULL DEFAULT 0 COMMENT '库内乐观锁版本，用于冲突覆盖判定',
    created_at    DATETIME,
    updated_at    DATETIME,
    PRIMARY KEY (id),
    KEY idx_wpr_batch (batch_no),
    KEY idx_wpr_year_week_code (plan_year, week_no, product_code),
    KEY idx_wpr_bizkey (plan_year, week_no, factory, project, customer_no, product_code, package_qty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='周计划行(按8D号)';

CREATE TABLE IF NOT EXISTS t_weekly_plan_shift_qty (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    row_id     BIGINT       NOT NULL COMMENT '关联 t_weekly_plan_row.id',
    batch_no   VARCHAR(64)  NOT NULL COMMENT '冗余批次号，便于按批清理',
    plan_date  DATE         NOT NULL COMMENT '当天日期(补齐年份后)',
    shift      VARCHAR(8)   NOT NULL COMMENT 'DAY=白班 / NIGHT=夜班',
    qty        DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '当班数量，空按0',
    PRIMARY KEY (id),
    KEY idx_wpsq_row (row_id),
    KEY idx_wpsq_batch (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='周计划班次数量(按日期+班次行存)';
