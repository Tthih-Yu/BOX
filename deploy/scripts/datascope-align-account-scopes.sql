-- 仅补充新送料账号的历史区域别名，不修改业务数据。
-- 保留原有新区域，同时加入历史任务/映射实际使用的旧值。
START TRANSACTION;

-- 500004：LP/H19 历史数据
INSERT IGNORE INTO sys_user_delivery_area (user_id, delivery_area)
SELECT id, area FROM sys_user CROSS JOIN
  (SELECT 'H19房间' AS area UNION ALL SELECT 'YJ-LP' UNION ALL SELECT 'LP') x
WHERE username = '500004';

-- 500005：IP 历史数据
INSERT IGNORE INTO sys_user_delivery_area (user_id, delivery_area)
SELECT id, area FROM sys_user CROSS JOIN
  (SELECT 'T18FL4-IP' AS area UNION ALL SELECT 'T1GC-IP') x
WHERE username = '500005';

SELECT u.username, GROUP_CONCAT(a.delivery_area ORDER BY a.delivery_area) AS delivery_areas
FROM sys_user u JOIN sys_user_delivery_area a ON a.user_id = u.id
WHERE u.username IN ('500004','500005')
GROUP BY u.username;

COMMIT;
