-- DataScope 已应用批次恢复模板（会修改数据，只能在批准的恢复窗口执行）
-- 仅当业务表当前值仍等于本批次新值时恢复，避免覆盖回填后的正常业务修改。
SET @batch_no = 'REPLACE_WITH_APPLIED_BATCH_NO';

START TRANSACTION;

SELECT batch_no, status, applied_at FROM datascope_backfill_batch
WHERE batch_no = @batch_no AND status = 'APPLIED' FOR UPDATE;

UPDATE t_replenishment_task t JOIN datascope_backfill_candidate c ON c.record_id=t.id AND c.object_type='TASK'
SET t.factory=c.old_factory,t.delivery_area=c.old_delivery_area,c.recovered_at=NOW()
WHERE c.batch_no=@batch_no AND EXISTS (SELECT 1 FROM datascope_backfill_batch b WHERE b.batch_no=c.batch_no AND b.status='APPLIED') AND c.applied_at IS NOT NULL AND c.recovered_at IS NULL
  AND (t.factory <=> c.new_factory) AND (t.delivery_area <=> c.new_delivery_area);
UPDATE t_print_job t JOIN datascope_backfill_candidate c ON c.record_id=t.id AND c.object_type='PRINT_JOB'
SET t.factory=c.old_factory,t.delivery_area=c.old_delivery_area,c.recovered_at=NOW()
WHERE c.batch_no=@batch_no AND EXISTS (SELECT 1 FROM datascope_backfill_batch b WHERE b.batch_no=c.batch_no AND b.status='APPLIED') AND c.applied_at IS NOT NULL AND c.recovered_at IS NULL
  AND (t.factory <=> c.new_factory) AND (t.delivery_area <=> c.new_delivery_area);
UPDATE t_box t JOIN datascope_backfill_candidate c ON c.record_id=t.id AND c.object_type='BOX'
SET t.factory=c.old_factory,t.delivery_area=c.old_delivery_area,c.recovered_at=NOW()
WHERE c.batch_no=@batch_no AND EXISTS (SELECT 1 FROM datascope_backfill_batch b WHERE b.batch_no=c.batch_no AND b.status='APPLIED') AND c.applied_at IS NOT NULL AND c.recovered_at IS NULL
  AND (t.factory <=> c.new_factory) AND (t.delivery_area <=> c.new_delivery_area);
UPDATE t_label t JOIN datascope_backfill_candidate c ON c.record_id=t.id AND c.object_type='LABEL'
SET t.factory=c.old_factory,t.delivery_area=c.old_delivery_area,c.recovered_at=NOW()
WHERE c.batch_no=@batch_no AND EXISTS (SELECT 1 FROM datascope_backfill_batch b WHERE b.batch_no=c.batch_no AND b.status='APPLIED') AND c.applied_at IS NOT NULL AND c.recovered_at IS NULL
  AND (t.factory <=> c.new_factory) AND (t.delivery_area <=> c.new_delivery_area);
UPDATE t_inventory t JOIN datascope_backfill_candidate c ON c.record_id=t.id AND c.object_type='INVENTORY'
SET t.factory=c.old_factory,t.delivery_area=c.old_delivery_area,c.recovered_at=NOW()
WHERE c.batch_no=@batch_no AND EXISTS (SELECT 1 FROM datascope_backfill_batch b WHERE b.batch_no=c.batch_no AND b.status='APPLIED') AND c.applied_at IS NOT NULL AND c.recovered_at IS NULL
  AND (t.factory <=> c.new_factory) AND (t.delivery_area <=> c.new_delivery_area);

UPDATE t_production_plan t JOIN datascope_backfill_candidate c ON c.record_id=t.id AND c.object_type='PRODUCTION_PLAN'
SET t.factory=c.old_factory,c.recovered_at=NOW()
WHERE c.batch_no=@batch_no AND EXISTS (SELECT 1 FROM datascope_backfill_batch b WHERE b.batch_no=c.batch_no AND b.status='APPLIED') AND c.applied_at IS NOT NULL AND c.recovered_at IS NULL AND (t.factory <=> c.new_factory);
UPDATE t_material_demand t JOIN datascope_backfill_candidate c ON c.record_id=t.id AND c.object_type='MATERIAL_DEMAND'
SET t.factory=c.old_factory,c.recovered_at=NOW()
WHERE c.batch_no=@batch_no AND EXISTS (SELECT 1 FROM datascope_backfill_batch b WHERE b.batch_no=c.batch_no AND b.status='APPLIED') AND c.applied_at IS NOT NULL AND c.recovered_at IS NULL AND (t.factory <=> c.new_factory);
UPDATE t_purchase_requirement t JOIN datascope_backfill_candidate c ON c.record_id=t.id AND c.object_type='PURCHASE_REQUIREMENT'
SET t.factory=c.old_factory,c.recovered_at=NOW()
WHERE c.batch_no=@batch_no AND EXISTS (SELECT 1 FROM datascope_backfill_batch b WHERE b.batch_no=c.batch_no AND b.status='APPLIED') AND c.applied_at IS NOT NULL AND c.recovered_at IS NULL AND (t.factory <=> c.new_factory);

UPDATE datascope_backfill_batch b
SET b.status='RECOVERED',b.recovered_at=NOW()
WHERE b.batch_no=@batch_no AND b.status='APPLIED'
  AND NOT EXISTS (SELECT 1 FROM datascope_backfill_candidate c
                  WHERE c.batch_no=@batch_no AND c.applied_at IS NOT NULL AND c.recovered_at IS NULL);

-- 默认回滚；验证恢复结果后由批准执行人显式改为 COMMIT。
ROLLBACK;
