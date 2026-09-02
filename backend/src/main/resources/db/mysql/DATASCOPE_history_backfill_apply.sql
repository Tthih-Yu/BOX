-- DataScope 已审批历史候选回填模板（会修改数据，只能在备份验证后的批准窗口执行）
-- 执行前替换批次号；占位值无法命中合法批次。
SET @batch_no = 'REPLACE_WITH_APPROVED_BATCH_NO';

START TRANSACTION;

-- 锁定并核验批次。执行者必须确认仅返回一行 APPROVED。
SELECT batch_no, status, approved_by, approved_at
FROM datascope_backfill_batch
WHERE batch_no = @batch_no AND status = 'APPROVED'
FOR UPDATE;

-- 每次 UPDATE 都要求：批次已批准、候选已批准、尚未应用、当前值仍等于候选原值。
UPDATE t_replenishment_task t
JOIN datascope_backfill_candidate c ON c.record_id = t.id AND c.object_type = 'TASK'
JOIN datascope_backfill_batch b ON b.batch_no = c.batch_no AND b.status = 'APPROVED'
SET t.factory = c.new_factory, t.delivery_area = c.new_delivery_area, c.applied_at = NOW()
WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED' AND c.applied_at IS NULL
  AND (t.factory <=> c.old_factory) AND (t.delivery_area <=> c.old_delivery_area)
  AND NULLIF(TRIM(c.new_factory), '') IS NOT NULL
  AND NULLIF(TRIM(c.new_delivery_area), '') IS NOT NULL;

UPDATE t_print_job p
JOIN datascope_backfill_candidate c ON c.record_id = p.id AND c.object_type = 'PRINT_JOB'
JOIN datascope_backfill_batch b ON b.batch_no = c.batch_no AND b.status = 'APPROVED'
SET p.factory = c.new_factory, p.delivery_area = c.new_delivery_area, c.applied_at = NOW()
WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED' AND c.applied_at IS NULL
  AND (p.factory <=> c.old_factory) AND (p.delivery_area <=> c.old_delivery_area)
  AND NULLIF(TRIM(c.new_factory), '') IS NOT NULL
  AND NULLIF(TRIM(c.new_delivery_area), '') IS NOT NULL;

UPDATE t_box x
JOIN datascope_backfill_candidate c ON c.record_id = x.id AND c.object_type = 'BOX'
JOIN datascope_backfill_batch b ON b.batch_no = c.batch_no AND b.status = 'APPROVED'
SET x.factory = c.new_factory, x.delivery_area = c.new_delivery_area, c.applied_at = NOW()
WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED' AND c.applied_at IS NULL
  AND (x.factory <=> c.old_factory) AND (x.delivery_area <=> c.old_delivery_area)
  AND NULLIF(TRIM(c.new_factory), '') IS NOT NULL
  AND NULLIF(TRIM(c.new_delivery_area), '') IS NOT NULL;

UPDATE t_label x
JOIN datascope_backfill_candidate c ON c.record_id = x.id AND c.object_type = 'LABEL'
JOIN datascope_backfill_batch b ON b.batch_no = c.batch_no AND b.status = 'APPROVED'
SET x.factory = c.new_factory, x.delivery_area = c.new_delivery_area, c.applied_at = NOW()
WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED' AND c.applied_at IS NULL
  AND (x.factory <=> c.old_factory) AND (x.delivery_area <=> c.old_delivery_area)
  AND NULLIF(TRIM(c.new_factory), '') IS NOT NULL
  AND NULLIF(TRIM(c.new_delivery_area), '') IS NOT NULL;

UPDATE t_inventory x
JOIN datascope_backfill_candidate c ON c.record_id = x.id AND c.object_type = 'INVENTORY'
JOIN datascope_backfill_batch b ON b.batch_no = c.batch_no AND b.status = 'APPROVED'
SET x.factory = c.new_factory, x.delivery_area = c.new_delivery_area, c.applied_at = NOW()
WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED' AND c.applied_at IS NULL
  AND (x.factory <=> c.old_factory) AND (x.delivery_area <=> c.old_delivery_area)
  AND NULLIF(TRIM(c.new_factory), '') IS NOT NULL
  AND NULLIF(TRIM(c.new_delivery_area), '') IS NOT NULL;

-- FACTORY_ONLY 计划链。
UPDATE t_production_plan x
JOIN datascope_backfill_candidate c ON c.record_id = x.id AND c.object_type = 'PRODUCTION_PLAN'
JOIN datascope_backfill_batch b ON b.batch_no = c.batch_no AND b.status = 'APPROVED'
SET x.factory = c.new_factory, c.applied_at = NOW()
WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED' AND c.applied_at IS NULL
  AND (x.factory <=> c.old_factory) AND NULLIF(TRIM(c.new_factory), '') IS NOT NULL;

UPDATE t_material_demand x
JOIN datascope_backfill_candidate c ON c.record_id = x.id AND c.object_type = 'MATERIAL_DEMAND'
JOIN datascope_backfill_batch b ON b.batch_no = c.batch_no AND b.status = 'APPROVED'
SET x.factory = c.new_factory, c.applied_at = NOW()
WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED' AND c.applied_at IS NULL
  AND (x.factory <=> c.old_factory) AND NULLIF(TRIM(c.new_factory), '') IS NOT NULL;

UPDATE t_purchase_requirement x
JOIN datascope_backfill_candidate c ON c.record_id = x.id AND c.object_type = 'PURCHASE_REQUIREMENT'
JOIN datascope_backfill_batch b ON b.batch_no = c.batch_no AND b.status = 'APPROVED'
SET x.factory = c.new_factory, c.applied_at = NOW()
WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED' AND c.applied_at IS NULL
  AND (x.factory <=> c.old_factory) AND NULLIF(TRIM(c.new_factory), '') IS NOT NULL;

UPDATE datascope_backfill_batch
SET status = 'APPLIED', applied_at = NOW()
WHERE batch_no = @batch_no AND status = 'APPROVED'
  AND NOT EXISTS (
      SELECT 1 FROM datascope_backfill_candidate c
      WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED' AND c.applied_at IS NULL
  );

-- 执行校验脚本确认后，人工改为 COMMIT；默认回滚防止误执行。
ROLLBACK;
