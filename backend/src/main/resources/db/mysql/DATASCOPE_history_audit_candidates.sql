-- DataScope 历史数据审计与候选（只读）
-- 前提：V0.9.1-V0.9.8 Expand Schema 已在隔离验证库执行。
-- 本脚本只有 SELECT/CTE，不更新数据；候选仍须业务审批。

-- 1. 主要对象范围完整度与非法字典值
SELECT 'task' object_type, COUNT(*) total,
       SUM(NULLIF(TRIM(factory), '') IS NULL) missing_factory,
       SUM(NULLIF(TRIM(delivery_area), '') IS NULL) missing_area,
       SUM(NULLIF(TRIM(factory), '') IS NOT NULL AND NOT EXISTS (
           SELECT 1 FROM sys_factory f WHERE f.factory_code = TRIM(t.factory))) invalid_factory,
       SUM(NULLIF(TRIM(delivery_area), '') IS NOT NULL AND NOT EXISTS (
           SELECT 1 FROM sys_delivery_area a
           WHERE a.factory_code = TRIM(t.factory) AND a.area_code = TRIM(t.delivery_area))) invalid_area
FROM t_replenishment_task t
UNION ALL
SELECT 'mapping', COUNT(*),
       SUM(NULLIF(TRIM(factory), '') IS NULL),
       SUM(NULLIF(TRIM(delivery_area), '') IS NULL),
       SUM(NULLIF(TRIM(factory), '') IS NOT NULL AND NOT EXISTS (
           SELECT 1 FROM sys_factory f WHERE f.factory_code = TRIM(m.factory))),
       SUM(NULLIF(TRIM(delivery_area), '') IS NOT NULL AND NOT EXISTS (
           SELECT 1 FROM sys_delivery_area a
           WHERE a.factory_code = TRIM(m.factory) AND a.area_code = TRIM(m.delivery_area)))
FROM m_material_mapping m
UNION ALL
SELECT 'print_job', COUNT(*),
       SUM(NULLIF(TRIM(factory), '') IS NULL),
       SUM(NULLIF(TRIM(delivery_area), '') IS NULL),
       SUM(NULLIF(TRIM(factory), '') IS NOT NULL AND NOT EXISTS (
           SELECT 1 FROM sys_factory f WHERE f.factory_code = TRIM(p.factory))),
       SUM(NULLIF(TRIM(delivery_area), '') IS NOT NULL AND NOT EXISTS (
           SELECT 1 FROM sys_delivery_area a
           WHERE a.factory_code = TRIM(p.factory) AND a.area_code = TRIM(p.delivery_area)))
FROM t_print_job p;

-- 5. Box / Label：仅 warehouse_code 对应唯一 Mapping 范围时生成自动候选。
WITH warehouse_scope AS (
    SELECT TRIM(warehouse_code) warehouse_code,
           COUNT(*) mapping_rows,
           COUNT(DISTINCT CONCAT(TRIM(factory), '\0', TRIM(delivery_area))) scope_count,
           MIN(TRIM(factory)) candidate_factory,
           MIN(TRIM(delivery_area)) candidate_delivery_area
    FROM m_material_mapping
    WHERE enabled = TRUE
      AND NULLIF(TRIM(warehouse_code), '') IS NOT NULL
      AND NULLIF(TRIM(factory), '') IS NOT NULL
      AND NULLIF(TRIM(delivery_area), '') IS NOT NULL
    GROUP BY TRIM(warehouse_code)
)
SELECT 'box' object_type, b.id, b.box_code business_no, b.warehouse_code,
       b.factory old_factory, b.delivery_area old_delivery_area,
       ws.candidate_factory, ws.candidate_delivery_area,
       CASE WHEN ws.warehouse_code IS NULL THEN 'NO_CANDIDATE'
            WHEN ws.scope_count <> 1 THEN 'MULTIPLE_SCOPES'
            ELSE 'AUTO_CANDIDATE' END candidate_status
FROM t_box b
LEFT JOIN warehouse_scope ws ON ws.warehouse_code = TRIM(b.warehouse_code)
WHERE NULLIF(TRIM(b.factory), '') IS NULL OR NULLIF(TRIM(b.delivery_area), '') IS NULL
UNION ALL
SELECT 'label', l.id, l.label_code, l.warehouse_code,
       l.factory, l.delivery_area,
       ws.candidate_factory, ws.candidate_delivery_area,
       CASE WHEN ws.warehouse_code IS NULL THEN 'NO_CANDIDATE'
            WHEN ws.scope_count <> 1 THEN 'MULTIPLE_SCOPES'
            ELSE 'AUTO_CANDIDATE' END
FROM t_label l
LEFT JOIN warehouse_scope ws ON ws.warehouse_code = TRIM(l.warehouse_code)
WHERE NULLIF(TRIM(l.factory), '') IS NULL OR NULLIF(TRIM(l.delivery_area), '') IS NULL
ORDER BY object_type, candidate_status, id;

-- 6. Inventory：历史规则未批准自动继承 Mapping，只输出人工复核建议。
-- 即使 suggestion_status=REVIEW_CANDIDATE，也不得直接进入自动 UPDATE。
WITH inventory_scope AS (
    SELECT TRIM(warehouse_code) warehouse_code,
           TRIM(warehouse_material_code) warehouse_material_code,
           COUNT(DISTINCT CONCAT(TRIM(factory), '\0', TRIM(delivery_area))) scope_count,
           MIN(TRIM(factory)) suggested_factory,
           MIN(TRIM(delivery_area)) suggested_delivery_area
    FROM m_material_mapping
    WHERE enabled = TRUE
      AND NULLIF(TRIM(warehouse_code), '') IS NOT NULL
      AND NULLIF(TRIM(warehouse_material_code), '') IS NOT NULL
      AND NULLIF(TRIM(factory), '') IS NOT NULL
      AND NULLIF(TRIM(delivery_area), '') IS NOT NULL
    GROUP BY TRIM(warehouse_code), TRIM(warehouse_material_code)
)
SELECT i.id, i.warehouse_code, i.location_code, i.warehouse_material_code,
       i.factory old_factory, i.delivery_area old_delivery_area,
       s.suggested_factory, s.suggested_delivery_area,
       CASE WHEN s.warehouse_code IS NULL THEN 'NO_SUGGESTION'
            WHEN s.scope_count <> 1 THEN 'MULTIPLE_SCOPES'
            ELSE 'REVIEW_CANDIDATE' END suggestion_status
FROM t_inventory i
LEFT JOIN inventory_scope s
  ON s.warehouse_code = TRIM(i.warehouse_code)
 AND s.warehouse_material_code = TRIM(i.warehouse_material_code)
WHERE NULLIF(TRIM(i.factory), '') IS NULL OR NULLIF(TRIM(i.delivery_area), '') IS NULL
ORDER BY suggestion_status, i.id;

-- 7. 计划链：ProductionPlan 没有可信历史上游，必须人工定厂；下游只从完整上游继承。
SELECT p.id, p.plan_no, p.line_code, p.station_code, p.factory old_factory,
       'MANUAL_FACTORY_REQUIRED' candidate_status
FROM t_production_plan p
WHERE NULLIF(TRIM(p.factory), '') IS NULL
ORDER BY p.id;

SELECT d.id, d.demand_no, d.plan_no, d.factory old_factory,
       p.factory candidate_factory,
       CASE WHEN p.id IS NULL THEN 'PLAN_NOT_FOUND'
            WHEN NULLIF(TRIM(p.factory), '') IS NULL THEN 'PLAN_SCOPE_INCOMPLETE'
            ELSE 'AUTO_CANDIDATE' END candidate_status
FROM t_material_demand d
LEFT JOIN t_production_plan p ON p.plan_no = d.plan_no
WHERE NULLIF(TRIM(d.factory), '') IS NULL
ORDER BY candidate_status, d.id;

SELECT r.id, r.purchase_no, r.demand_no, r.factory old_factory,
       d.factory candidate_factory,
       CASE WHEN d.id IS NULL THEN 'DEMAND_NOT_FOUND'
            WHEN NULLIF(TRIM(d.factory), '') IS NULL THEN 'DEMAND_SCOPE_INCOMPLETE'
            ELSE 'AUTO_CANDIDATE' END candidate_status
FROM t_purchase_requirement r
LEFT JOIN t_material_demand d ON d.demand_no = r.demand_no
WHERE NULLIF(TRIM(r.factory), '') IS NULL
ORDER BY candidate_status, r.id;

-- 8. ScanLog：历史权威来源尚未最终批准，仅输出 Label/Box 一致性复核清单。
SELECT s.id, s.label_code, s.box_code, s.action, s.scan_at,
       s.factory old_factory, s.delivery_area old_delivery_area,
       COALESCE(l.factory, b.factory) suggested_factory,
       COALESCE(l.delivery_area, b.delivery_area) suggested_delivery_area,
       CASE
           WHEN l.id IS NULL AND b.id IS NULL THEN 'NO_SUGGESTION'
           WHEN (l.id IS NOT NULL AND (NULLIF(TRIM(l.factory), '') IS NULL OR NULLIF(TRIM(l.delivery_area), '') IS NULL))
             OR (b.id IS NOT NULL AND (NULLIF(TRIM(b.factory), '') IS NULL OR NULLIF(TRIM(b.delivery_area), '') IS NULL))
             THEN 'SOURCE_SCOPE_INCOMPLETE'
           WHEN l.id IS NOT NULL AND b.id IS NOT NULL
             AND (TRIM(l.factory) <> TRIM(b.factory) OR TRIM(l.delivery_area) <> TRIM(b.delivery_area))
             THEN 'SOURCE_SCOPE_CONFLICT'
           ELSE 'REVIEW_CANDIDATE'
       END suggestion_status
FROM log_scan s
LEFT JOIN t_label l ON l.label_code = s.label_code
LEFT JOIN t_box b ON b.box_code = s.box_code
WHERE NULLIF(TRIM(s.factory), '') IS NULL OR NULLIF(TRIM(s.delivery_area), '') IS NULL
ORDER BY suggestion_status, s.id;

-- 9. Outbox：旧事件 topic/business_no 的权威映射尚未批准，保持人工清单。
SELECT id, event_no, topic, business_no, scope_type, factory, delivery_area,
       'MANUAL_SCOPE_REQUIRED' candidate_status
FROM sys_outbox_event
WHERE NULLIF(TRIM(scope_type), '') IS NULL
   OR (scope_type = 'FACTORY_ONLY' AND NULLIF(TRIM(factory), '') IS NULL)
   OR (scope_type = 'FACTORY_AREA' AND
       (NULLIF(TRIM(factory), '') IS NULL OR NULLIF(TRIM(delivery_area), '') IS NULL))
ORDER BY id;

-- 2. Task -> Mapping 归属候选分类。
-- 只有 warehouse_code + material_code 对应唯一非空范围时标记 AUTO_CANDIDATE；
-- 无候选或多范围候选必须人工处理，禁止取第一条。
WITH mapping_scope AS (
    SELECT TRIM(warehouse_code) warehouse_code,
           TRIM(line_material_code) material_code,
           COUNT(*) mapping_rows,
           COUNT(DISTINCT CONCAT(TRIM(factory), '\0', TRIM(delivery_area))) scope_count,
           MIN(TRIM(factory)) candidate_factory,
           MIN(TRIM(delivery_area)) candidate_delivery_area
    FROM m_material_mapping
    WHERE enabled = TRUE
      AND NULLIF(TRIM(warehouse_code), '') IS NOT NULL
      AND NULLIF(TRIM(line_material_code), '') IS NOT NULL
      AND NULLIF(TRIM(factory), '') IS NOT NULL
      AND NULLIF(TRIM(delivery_area), '') IS NOT NULL
    GROUP BY TRIM(warehouse_code), TRIM(line_material_code)
)
SELECT t.id, t.task_no, t.factory old_factory, t.delivery_area old_delivery_area,
       t.warehouse_code, t.material_code,
       ms.candidate_factory, ms.candidate_delivery_area,
       CASE
           WHEN ms.warehouse_code IS NULL THEN 'NO_CANDIDATE'
           WHEN ms.scope_count <> 1 THEN 'MULTIPLE_SCOPES'
           WHEN NULLIF(TRIM(t.factory), '') IS NOT NULL
             AND TRIM(t.factory) <> ms.candidate_factory THEN 'FACTORY_CONFLICT'
           WHEN NULLIF(TRIM(t.delivery_area), '') IS NOT NULL
             AND TRIM(t.delivery_area) <> ms.candidate_delivery_area THEN 'DELIVERY_AREA_CONFLICT'
           ELSE 'AUTO_CANDIDATE'
       END candidate_status,
       ms.mapping_rows, ms.scope_count
FROM t_replenishment_task t
LEFT JOIN mapping_scope ms
  ON ms.warehouse_code = TRIM(t.warehouse_code)
 AND ms.material_code = TRIM(t.material_code)
WHERE NULLIF(TRIM(t.factory), '') IS NULL
   OR NULLIF(TRIM(t.delivery_area), '') IS NULL
ORDER BY candidate_status, t.id;

-- 3. PrintJob 仅从唯一 Task 继承候选；Task 自身范围不完整时不产生自动候选。
SELECT p.id, p.print_job_no, p.task_no,
       p.factory old_factory, p.delivery_area old_delivery_area,
       t.factory candidate_factory, t.delivery_area candidate_delivery_area,
       CASE
           WHEN t.id IS NULL THEN 'TASK_NOT_FOUND'
           WHEN NULLIF(TRIM(t.factory), '') IS NULL
             OR NULLIF(TRIM(t.delivery_area), '') IS NULL THEN 'TASK_SCOPE_INCOMPLETE'
           ELSE 'AUTO_CANDIDATE'
       END candidate_status
FROM t_print_job p
LEFT JOIN t_replenishment_task t ON t.task_no = p.task_no
WHERE NULLIF(TRIM(p.factory), '') IS NULL
   OR NULLIF(TRIM(p.delivery_area), '') IS NULL
ORDER BY candidate_status, p.id;

-- 4. 关联覆盖率（NULLIF 防止空表除零）。
SELECT 'task_to_mapping' relation_name, COUNT(*) source_rows,
       SUM(EXISTS (
           SELECT 1 FROM m_material_mapping m
           WHERE m.enabled = TRUE
             AND m.warehouse_code = t.warehouse_code
             AND m.line_material_code = t.material_code)) matched_rows,
       ROUND(100 * SUM(EXISTS (
           SELECT 1 FROM m_material_mapping m
           WHERE m.enabled = TRUE
             AND m.warehouse_code = t.warehouse_code
             AND m.line_material_code = t.material_code)) / NULLIF(COUNT(*), 0), 2) matched_percent
FROM t_replenishment_task t
UNION ALL
SELECT 'print_job_to_task', COUNT(*),
       SUM(EXISTS (SELECT 1 FROM t_replenishment_task t WHERE t.task_no = p.task_no)),
       ROUND(100 * SUM(EXISTS (
           SELECT 1 FROM t_replenishment_task t WHERE t.task_no = p.task_no)) / NULLIF(COUNT(*), 0), 2)
FROM t_print_job p;
