-- 先于新版后端执行。新增可空列，旧版后端继续正常读写。
-- m_material_mapping.description 已由现有实体维护；发布前先核实生产表中存在该列。
ALTER TABLE t_replenishment_task
    ADD COLUMN mapping_description VARCHAR(255) NULL COMMENT '创建任务时的料号映射描述';

-- 仅补齐尚未生成打印作业、且仓库代号在同一工厂和区域唯一的待打印任务。
-- 已打印任务及无法唯一定位映射的历史任务不改变。
UPDATE t_replenishment_task t
JOIN (
    SELECT factory, delivery_area, warehouse_code, MAX(description) AS description
    FROM m_material_mapping
    GROUP BY factory, delivery_area, warehouse_code
    HAVING COUNT(*) = 1 AND MAX(description) IS NOT NULL AND TRIM(MAX(description)) <> ''
) m ON m.factory = t.factory
   AND m.delivery_area = t.delivery_area
   AND m.warehouse_code = t.warehouse_code
SET t.mapping_description = m.description
WHERE t.mapping_description IS NULL
  AND COALESCE(t.print_generated, 0) = 0
  AND t.print_job_no IS NULL;
