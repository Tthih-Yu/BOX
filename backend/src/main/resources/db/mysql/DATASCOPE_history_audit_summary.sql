-- DataScope 历史范围候选汇总（只读，不含业务编号）
-- 用于生产审批前评估；逐条候选仅使用受限的 DATASCOPE_history_audit_candidates.sql 报告。

SELECT 'scope_completeness' AS report_type, 'task' AS object_type,
       COUNT(*) AS source_rows,
       SUM(NULLIF(TRIM(factory), '') IS NULL) AS missing_factory,
       SUM(NULLIF(TRIM(delivery_area), '') IS NULL) AS missing_delivery_area,
       NULL AS candidate_status,
       NULL AS matched_rows,
       NULL AS matched_percent
FROM t_replenishment_task
UNION ALL
SELECT 'scope_completeness', 'print_job', COUNT(*),
       SUM(NULLIF(TRIM(factory), '') IS NULL),
       SUM(NULLIF(TRIM(delivery_area), '') IS NULL),
       NULL, NULL, NULL
FROM t_print_job;

WITH mapping_scope AS (
    SELECT TRIM(warehouse_code) AS warehouse_code,
           TRIM(line_material_code) AS material_code,
           COUNT(DISTINCT CONCAT(TRIM(factory), '\0', TRIM(delivery_area))) AS scope_count,
           MIN(TRIM(factory)) AS candidate_factory,
           MIN(TRIM(delivery_area)) AS candidate_delivery_area
    FROM m_material_mapping
    WHERE enabled = TRUE
      AND NULLIF(TRIM(warehouse_code), '') IS NOT NULL
      AND NULLIF(TRIM(line_material_code), '') IS NOT NULL
      AND NULLIF(TRIM(factory), '') IS NOT NULL
      AND NULLIF(TRIM(delivery_area), '') IS NOT NULL
    GROUP BY TRIM(warehouse_code), TRIM(line_material_code)
), candidate_rows AS (
    SELECT 'task' AS object_type,
           CASE WHEN ms.warehouse_code IS NULL THEN 'NO_CANDIDATE'
                WHEN ms.scope_count <> 1 THEN 'MULTIPLE_SCOPES'
                WHEN NULLIF(TRIM(t.factory), '') IS NOT NULL
                  AND TRIM(t.factory) <> ms.candidate_factory THEN 'FACTORY_CONFLICT'
                WHEN NULLIF(TRIM(t.delivery_area), '') IS NOT NULL
                  AND TRIM(t.delivery_area) <> ms.candidate_delivery_area THEN 'DELIVERY_AREA_CONFLICT'
                ELSE 'AUTO_CANDIDATE' END AS candidate_status
    FROM t_replenishment_task t
    LEFT JOIN mapping_scope ms
      ON ms.warehouse_code = TRIM(t.warehouse_code)
     AND ms.material_code = TRIM(t.material_code)
    WHERE NULLIF(TRIM(t.factory), '') IS NULL
       OR NULLIF(TRIM(t.delivery_area), '') IS NULL
    UNION ALL
    SELECT 'print_job',
           CASE WHEN t.id IS NULL THEN 'TASK_NOT_FOUND'
                WHEN NULLIF(TRIM(t.factory), '') IS NULL
                  OR NULLIF(TRIM(t.delivery_area), '') IS NULL THEN 'TASK_SCOPE_INCOMPLETE'
                ELSE 'AUTO_CANDIDATE' END
    FROM t_print_job p
    LEFT JOIN t_replenishment_task t ON t.task_no = p.task_no
    WHERE NULLIF(TRIM(p.factory), '') IS NULL
       OR NULLIF(TRIM(p.delivery_area), '') IS NULL
)
SELECT 'candidate_summary' AS report_type, object_type,
       COUNT(*) AS source_rows,
       NULL AS missing_factory,
       NULL AS missing_delivery_area,
       candidate_status,
       NULL AS matched_rows,
       NULL AS matched_percent
FROM candidate_rows
GROUP BY object_type, candidate_status
ORDER BY object_type, candidate_status;

SELECT 'relation_coverage' AS report_type, 'task_to_mapping' AS object_type,
       COUNT(*) AS source_rows,
       NULL AS missing_factory,
       NULL AS missing_delivery_area,
       NULL AS candidate_status,
       SUM(EXISTS (
           SELECT 1 FROM m_material_mapping m
           WHERE m.enabled = TRUE
             AND m.warehouse_code = t.warehouse_code
             AND m.line_material_code = t.material_code
       )) AS matched_rows,
       ROUND(100 * SUM(EXISTS (
           SELECT 1 FROM m_material_mapping m
           WHERE m.enabled = TRUE
             AND m.warehouse_code = t.warehouse_code
             AND m.line_material_code = t.material_code
       )) / NULLIF(COUNT(*), 0), 2) AS matched_percent
FROM t_replenishment_task t
UNION ALL
SELECT 'relation_coverage', 'print_job_to_task', COUNT(*),
       NULL, NULL, NULL,
       SUM(EXISTS (SELECT 1 FROM t_replenishment_task t WHERE t.task_no = p.task_no)),
       ROUND(100 * SUM(EXISTS (
           SELECT 1 FROM t_replenishment_task t WHERE t.task_no = p.task_no)) / NULLIF(COUNT(*), 0), 2)
FROM t_print_job p;
