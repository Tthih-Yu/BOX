-- 阶段一：料号映射新增“单根用量”。用于周计划自动计算：组件需求量 = 计划数量 × 单根用量。
-- 允许为空，空表示尚未维护，不填充任何默认数据（数据由后期人员维护）。
-- 生产 MySQL 使用 ddl-auto=validate，必须先执行本 SQL 再启动新版后端。执行前请先备份。

ALTER TABLE m_material_mapping
    ADD COLUMN single_unit_usage DECIMAL(18,4) NULL
    COMMENT '单根用量：一件产品消耗该组件的数量；周计划计算用，空表示尚未维护';
