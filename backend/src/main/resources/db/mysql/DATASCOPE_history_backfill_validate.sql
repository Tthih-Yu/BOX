-- DataScope 回填批次校验（只读）
SET @batch_no = 'REPLACE_WITH_BATCH_NO';

SELECT b.batch_no, b.status, b.approved_by, b.approved_at, b.applied_at,
       COUNT(c.id) candidates,
       SUM(c.review_status = 'APPROVED') approved_candidates,
       SUM(c.applied_at IS NOT NULL) applied_candidates,
       SUM(c.recovered_at IS NOT NULL) recovered_candidates
FROM datascope_backfill_batch b
LEFT JOIN datascope_backfill_candidate c ON c.batch_no = b.batch_no
WHERE b.batch_no = @batch_no
GROUP BY b.batch_no, b.status, b.approved_by, b.approved_at, b.applied_at;

-- 应为 0：审批信息缺失、范围不完整或字典非法。
SELECT c.*
FROM datascope_backfill_candidate c
WHERE c.batch_no = @batch_no AND c.review_status = 'APPROVED'
  AND (c.reviewed_by IS NULL OR c.reviewed_at IS NULL
    OR NULLIF(TRIM(c.new_factory), '') IS NULL
    OR NOT EXISTS (SELECT 1 FROM sys_factory f WHERE f.factory_code = c.new_factory)
    OR (c.object_type IN ('TASK','PRINT_JOB','BOX','LABEL','INVENTORY','SCAN_LOG') AND
        (NULLIF(TRIM(c.new_delivery_area), '') IS NULL OR NOT EXISTS (
            SELECT 1 FROM sys_delivery_area a
            WHERE a.factory_code = c.new_factory AND a.area_code = c.new_delivery_area))));

-- 应为 0：已审批但未应用，通常表示原值在审批后发生变化。
SELECT object_type, record_id, business_no, old_factory, old_delivery_area,
       new_factory, new_delivery_area, apply_note
FROM datascope_backfill_candidate
WHERE batch_no = @batch_no AND review_status = 'APPROVED' AND applied_at IS NULL;
