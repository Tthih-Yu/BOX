-- DataScope Expand Schema 只读核验（MySQL 8+）
WITH expected(table_name, column_name) AS (
    -- V0.9.1/V0.9.2 是 DataScope 业务范围的前置迁移。
    SELECT 'm_material_mapping', 'factory' UNION ALL
    SELECT 't_replenishment_task', 'factory' UNION ALL
    SELECT 't_print_job', 'factory' UNION ALL
    SELECT 'sys_user', 'factory' UNION ALL
    SELECT 'sys_user', 'version' UNION ALL
    SELECT 't_inventory', 'factory' UNION ALL
    SELECT 't_inventory', 'delivery_area' UNION ALL
    SELECT 't_box', 'factory' UNION ALL
    SELECT 't_box', 'delivery_area' UNION ALL
    SELECT 't_label', 'factory' UNION ALL
    SELECT 't_label', 'delivery_area' UNION ALL
    SELECT 't_print_job', 'delivery_area' UNION ALL
    SELECT 't_production_plan', 'factory' UNION ALL
    SELECT 't_material_demand', 'factory' UNION ALL
    SELECT 't_purchase_requirement', 'factory' UNION ALL
    SELECT 'log_scan', 'factory' UNION ALL
    SELECT 'log_scan', 'delivery_area' UNION ALL
    SELECT 'sys_outbox_event', 'scope_type' UNION ALL
    SELECT 'sys_outbox_event', 'factory' UNION ALL
    SELECT 'sys_outbox_event', 'delivery_area'
)
SELECT e.table_name, e.column_name
FROM expected e
LEFT JOIN information_schema.columns c
  ON c.table_schema = DATABASE()
 AND c.table_name = e.table_name
 AND c.column_name = e.column_name
WHERE c.column_name IS NULL
ORDER BY e.table_name, e.column_name;

SELECT COUNT(*) AS delivery_area_count FROM sys_delivery_area;
SELECT COUNT(*) AS factory_count FROM sys_factory;
SELECT COUNT(*) AS user_area_relation_table
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'sys_user_delivery_area';
