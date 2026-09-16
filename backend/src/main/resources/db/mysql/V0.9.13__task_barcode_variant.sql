-- 六位仓库代号：为每个任务持久化无冲突的 CODE_128 变体编号。
-- 编号不进入扫码内容，仅用于选择不同的 A/B/C 合法编码路径。
ALTER TABLE t_replenishment_task
    ADD COLUMN barcode_variant_no INT NULL COMMENT '同仓库代号下的CODE_128变体编号，不进入扫码内容' AFTER warehouse_code;

UPDATE t_replenishment_task task
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY warehouse_code ORDER BY id) - 1 AS variant_no
    FROM t_replenishment_task
    WHERE warehouse_code REGEXP '^[0-9]{6}$'
) ranked ON ranked.id = task.id
SET task.barcode_variant_no = ranked.variant_no;

CREATE UNIQUE INDEX uk_task_warehouse_barcode_variant
    ON t_replenishment_task (warehouse_code, barcode_variant_no);
