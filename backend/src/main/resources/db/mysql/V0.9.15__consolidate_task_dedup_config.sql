-- 将“去重开关 + 秒数 + 旧分钟数”合并为一个参数：
-- task.dedup.window-seconds = 0 表示关闭，>=5 表示开启并使用对应秒数。
-- 迁移优先保持旧开关的实际效果，随后删除两个废弃键。
START TRANSACTION;

SET @dedup_enabled = (
    SELECT LOWER(TRIM(config_value))
    FROM sys_config
    WHERE config_key = 'task.dedup.enabled'
    LIMIT 1
);

SET @dedup_seconds = (
    SELECT TRIM(config_value)
    FROM sys_config
    WHERE config_key = 'task.dedup.window-seconds'
    LIMIT 1
);

SET @dedup_minutes = (
    SELECT TRIM(config_value)
    FROM sys_config
    WHERE config_key = 'task.dedup.window-minutes'
    LIMIT 1
);

SET @effective_dedup_seconds = CASE
    WHEN @dedup_enabled IS NOT NULL THEN
        CASE WHEN @dedup_enabled IN ('true', '1', 'on', 'yes') THEN
            CASE
                WHEN @dedup_seconds REGEXP '^[0-9]+$' AND CAST(@dedup_seconds AS UNSIGNED) > 0
                    THEN GREATEST(5, CAST(@dedup_seconds AS UNSIGNED))
                WHEN @dedup_minutes REGEXP '^[0-9]+$' AND CAST(@dedup_minutes AS UNSIGNED) > 0
                    THEN GREATEST(5, CAST(@dedup_minutes AS UNSIGNED) * 60)
                ELSE 60
            END
        ELSE 0 END
    WHEN @dedup_seconds REGEXP '^[0-9]+$' THEN
        CASE WHEN CAST(@dedup_seconds AS UNSIGNED) = 0
            THEN 0 ELSE GREATEST(5, CAST(@dedup_seconds AS UNSIGNED)) END
    WHEN @dedup_minutes REGEXP '^[0-9]+$' AND CAST(@dedup_minutes AS UNSIGNED) > 0
        THEN GREATEST(5, CAST(@dedup_minutes AS UNSIGNED) * 60)
    ELSE 0
END;

INSERT INTO sys_config (config_key, config_value, config_name, remark, editable, updated_at)
VALUES (
    'task.dedup.window-seconds',
    CAST(@effective_dedup_seconds AS CHAR),
    '补货任务去重时间窗(秒，0=关闭)',
    '单一去重参数：0表示关闭；大于等于5表示开启，并在对应秒数内禁止同一仓库代号重复生成。修改后立即生效。',
    1,
    NOW(6)
)
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    config_name = VALUES(config_name),
    remark = VALUES(remark),
    editable = VALUES(editable),
    updated_at = VALUES(updated_at);

DELETE FROM sys_config
WHERE config_key IN ('task.dedup.enabled', 'task.dedup.window-minutes');

COMMIT;
