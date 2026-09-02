#!/usr/bin/env sh
# 生产 DataScope 只读预检：不执行 Migration、不创建备份、不写入数据库。
set -eu

: "${DATASCOPE_PRODUCTION_READONLY_CONFIRM:?请设置 DATASCOPE_PRODUCTION_READONLY_CONFIRM=I_UNDERSTAND_PRODUCTION_READ_ONLY}"
if [ "$DATASCOPE_PRODUCTION_READONLY_CONFIRM" != "I_UNDERSTAND_PRODUCTION_READ_ONLY" ]; then
  echo "拒绝执行：未完成生产只读预检确认" >&2
  exit 2
fi

ENV_FILE="${DATASCOPE_ENV_FILE:-/etc/material-pull/material-pull.env}"
if [ ! -r "$ENV_FILE" ]; then
  echo "拒绝执行：无法读取生产环境文件 $ENV_FILE；请通过 sudo 运行，不要复制密码到命令行。" >&2
  exit 2
fi

set -a
. "$ENV_FILE"
set +a

MYSQL_USER="${MYSQL_USER:-${DB_USER:-${DB_USERNAME:-${SPRING_DATASOURCE_USERNAME:-}}}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}}"
MYSQL_HOST="${MYSQL_HOST:-${DB_HOST:-${DATABASE_HOST:-}}}"
MYSQL_PORT="${MYSQL_PORT:-${DB_PORT:-${DATABASE_PORT:-3306}}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-${DB_NAME:-${DB_DATABASE:-${DATABASE_NAME:-}}}}"

# 兼容仅配置 Spring JDBC URL 的生产环境。
if [ -z "$MYSQL_HOST" ] || [ -z "$MYSQL_DATABASE" ]; then
  jdbc_url="${SPRING_DATASOURCE_URL:-${JDBC_DATABASE_URL:-${DATABASE_URL:-${DB_URL:-}}}}"
  case "$jdbc_url" in
    jdbc:mysql://*/*)
      endpoint="${jdbc_url#jdbc:mysql://}"
      authority="${endpoint%%/*}"
      database_part="${endpoint#*/}"
      [ -n "$MYSQL_HOST" ] || MYSQL_HOST="${authority%%:*}"
      case "$authority" in
        *:*) MYSQL_PORT="${authority##*:}" ;;
      esac
      [ -n "$MYSQL_DATABASE" ] || MYSQL_DATABASE="${database_part%%\?*}"
      ;;
    mysql://*/*)
      endpoint="${jdbc_url#mysql://}"
      authority="${endpoint%%/*}"
      database_part="${endpoint#*/}"
      [ -n "$MYSQL_HOST" ] || MYSQL_HOST="${authority%%:*}"
      case "$authority" in
        *:*) MYSQL_PORT="${authority##*:}" ;;
      esac
      [ -n "$MYSQL_DATABASE" ] || MYSQL_DATABASE="${database_part%%\?*}"
      ;;
  esac
fi

# 与 application.yml 的 mysql profile 默认值保持一致：未在环境文件覆盖时连接本机 material_pull。
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_DATABASE="${MYSQL_DATABASE:-material_pull}"
: "${MYSQL_USER:?环境文件未提供 MySQL 用户名}"
: "${MYSQL_PASSWORD:?环境文件未提供 MySQL 密码}"
command -v mysql >/dev/null 2>&1 || { echo "缺少 mysql 客户端" >&2; exit 127; }

export MYSQL_PWD="$MYSQL_PASSWORD"
trap 'unset MYSQL_PWD MYSQL_PASSWORD' EXIT HUP INT TERM

mysql_cmd() {
  mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
    --database="$MYSQL_DATABASE" --batch --raw --init-command="SET SESSION TRANSACTION READ ONLY" "$@"
}

echo "== DataScope 生产只读预检 =="
echo "数据库：$MYSQL_DATABASE（主机与用户名已隐藏）"
preflight_exit=0

# 新账号在首次生产发布前必须尚未存在；只显示非敏感摘要，不读取密码字段。
mysql_cmd --table -e "SELECT DATABASE() AS database_name, VERSION() AS mysql_version; SELECT username, role, enabled FROM sys_user WHERE username IN ('500001','500002','500003','500004','500005') ORDER BY username;"
account_conflicts=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_user WHERE username IN ('500001','500002','500003','500004','500005')")
if [ "$account_conflicts" -gt 0 ]; then
  echo "结果：发现 $account_conflicts 个候选新增账号同名记录；发布前必须人工确认，脚本不会覆盖。"
  preflight_exit=5
else
  echo "新增账号冲突：未发现（首次初始化可继续评审）。"
fi

# V0.9.3 依赖此前 V0.9.1/V0.9.2 的三项范围字段；即使后续新表尚未创建，也必须单独确认。
baseline_missing_columns=$(mysql_cmd --skip-column-names -e "
WITH expected(table_name,column_name) AS (
 SELECT 'm_material_mapping','factory' UNION ALL
 SELECT 't_replenishment_task','factory' UNION ALL
 SELECT 't_print_job','factory'
)
SELECT COALESCE(GROUP_CONCAT(CONCAT(e.table_name,'.',e.column_name) ORDER BY e.table_name,e.column_name SEPARATOR ','), '')
FROM expected e LEFT JOIN information_schema.columns c
  ON c.table_schema=DATABASE() AND c.table_name=e.table_name AND c.column_name=e.column_name
WHERE c.column_name IS NULL")
if [ -n "$baseline_missing_columns" ]; then
  echo "结果：缺少 V0.9.1/V0.9.2 前置字段：$baseline_missing_columns"
  preflight_exit=4
else
  echo "前置字段：V0.9.1/V0.9.2 的 Mapping、Task、PrintJob factory 字段均存在。"
fi

missing_tables=$(mysql_cmd --skip-column-names -e "
WITH expected(table_name) AS (
  SELECT 'm_material_mapping' UNION ALL SELECT 't_replenishment_task' UNION ALL
  SELECT 't_print_job' UNION ALL SELECT 'sys_user' UNION ALL
  SELECT 't_inventory' UNION ALL SELECT 't_box' UNION ALL SELECT 't_label' UNION ALL
  SELECT 't_production_plan' UNION ALL SELECT 't_material_demand' UNION ALL
  SELECT 't_purchase_requirement' UNION ALL SELECT 'log_scan' UNION ALL
  SELECT 'sys_outbox_event' UNION ALL SELECT 'sys_user_delivery_area' UNION ALL
  SELECT 'sys_factory' UNION ALL SELECT 'sys_delivery_area' UNION ALL
  SELECT 'log_user_scope'
)
SELECT COALESCE(GROUP_CONCAT(e.table_name ORDER BY e.table_name SEPARATOR ','), '')
FROM expected e LEFT JOIN information_schema.tables t
  ON t.table_schema=DATABASE() AND t.table_name=e.table_name
WHERE t.table_name IS NULL")
if [ -n "$missing_tables" ]; then
  echo "结果：缺少 DataScope 所需表：$missing_tables"
  preflight_exit=4
  missing_columns=""
else
  missing_columns=$(mysql_cmd --skip-column-names -e "
WITH expected(table_name,column_name) AS (
 SELECT 'm_material_mapping','factory' UNION ALL
 SELECT 't_replenishment_task','factory' UNION ALL
 SELECT 't_print_job','factory' UNION ALL SELECT 't_print_job','delivery_area' UNION ALL
 SELECT 'sys_user','factory' UNION ALL SELECT 'sys_user','version' UNION ALL
 SELECT 't_inventory','factory' UNION ALL SELECT 't_inventory','delivery_area' UNION ALL
 SELECT 't_inventory','scope_location_material_hash' UNION ALL
 SELECT 't_box','factory' UNION ALL SELECT 't_box','delivery_area' UNION ALL
 SELECT 't_label','factory' UNION ALL SELECT 't_label','delivery_area' UNION ALL
 SELECT 't_production_plan','factory' UNION ALL SELECT 't_material_demand','factory' UNION ALL
 SELECT 't_purchase_requirement','factory' UNION ALL
 SELECT 'log_scan','factory' UNION ALL SELECT 'log_scan','delivery_area' UNION ALL
 SELECT 'sys_outbox_event','scope_type' UNION ALL SELECT 'sys_outbox_event','factory' UNION ALL
 SELECT 'sys_outbox_event','delivery_area' UNION ALL
 SELECT 'log_user_scope','action' UNION ALL SELECT 'log_user_scope','target_username' UNION ALL
 SELECT 'log_user_scope','created_at'
)
SELECT COALESCE(GROUP_CONCAT(CONCAT(e.table_name,'.',e.column_name) ORDER BY e.table_name,e.column_name SEPARATOR ','), '')
FROM expected e LEFT JOIN information_schema.columns c
  ON c.table_schema=DATABASE() AND c.table_name=e.table_name AND c.column_name=e.column_name
WHERE c.column_name IS NULL")
  if [ -n "$missing_columns" ]; then
    echo "结果：缺少 DataScope 字段：$missing_columns"
    preflight_exit=4
  fi
fi

missing_indexes=""
invalid_generated_columns=""
if [ -z "$missing_tables" ] && [ -z "$missing_columns" ]; then
  missing_indexes=$(mysql_cmd --skip-column-names -e "
WITH expected(table_name,index_name) AS (
 SELECT 'sys_user_delivery_area','uk_user_area' UNION ALL
 SELECT 'sys_user_delivery_area','idx_user_id' UNION ALL
 SELECT 'sys_user_delivery_area','idx_delivery_area' UNION ALL
 SELECT 'sys_delivery_area','uk_factory_area' UNION ALL
 SELECT 'sys_delivery_area','idx_factory' UNION ALL
 SELECT 't_inventory','idx_inventory_scope' UNION ALL
 SELECT 't_inventory','uk_inventory_scope_location_material' UNION ALL
 SELECT 't_box','idx_box_scope' UNION ALL SELECT 't_label','idx_label_scope' UNION ALL
 SELECT 't_print_job','idx_print_scope' UNION ALL
 SELECT 't_production_plan','idx_plan_factory' UNION ALL
 SELECT 't_material_demand','idx_demand_factory' UNION ALL
 SELECT 't_purchase_requirement','idx_purchase_factory' UNION ALL
 SELECT 'log_scan','idx_scan_scope' UNION ALL
 SELECT 'sys_outbox_event','idx_outbox_scope' UNION ALL
 SELECT 't_replenishment_task','idx_task_scope_created' UNION ALL
 SELECT 't_replenishment_task','idx_task_scope_status_created' UNION ALL
 SELECT 't_print_job','idx_print_scope_created' UNION ALL
 SELECT 'log_user_scope','idx_user_scope_audit_target_created' UNION ALL
 SELECT 'log_user_scope','idx_user_scope_audit_created'
)
SELECT COALESCE(GROUP_CONCAT(CONCAT(e.table_name,'.',e.index_name) ORDER BY e.table_name,e.index_name SEPARATOR ','), '')
FROM expected e LEFT JOIN information_schema.statistics s
  ON s.table_schema=DATABASE() AND s.table_name=e.table_name AND s.index_name=e.index_name
WHERE s.index_name IS NULL")
  if [ -n "$missing_indexes" ]; then
    echo "结果：缺少 DataScope 索引：$missing_indexes"
    preflight_exit=4
  fi

  inventory_hash_generated=$(mysql_cmd --skip-column-names -e "
SELECT COUNT(*) FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='t_inventory'
  AND column_name='scope_location_material_hash'
  AND generation_expression IS NOT NULL AND generation_expression <> ''")
  if [ "$inventory_hash_generated" != "1" ]; then
    invalid_generated_columns="t_inventory.scope_location_material_hash"
    echo "结果：Inventory 范围唯一键生成列缺失或不是生成列：$invalid_generated_columns"
    preflight_exit=4
  fi
fi

if [ -z "$missing_tables" ] && [ -z "$missing_columns" ] && [ -z "$missing_indexes" ] && [ -z "$invalid_generated_columns" ]; then
  mysql_cmd --table <<'SQL'

SELECT
  (SELECT COUNT(*) FROM sys_factory) AS factory_count,
  (SELECT COUNT(*) FROM sys_delivery_area) AS delivery_area_count,
  (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND (
      (table_name='sys_user_delivery_area' AND index_name IN ('uk_user_area','idx_user_id','idx_delivery_area')) OR
      (table_name='sys_delivery_area' AND index_name IN ('uk_factory_area','idx_factory')) OR
      (table_name='t_inventory' AND index_name IN ('idx_inventory_scope','uk_inventory_scope_location_material')) OR
      (table_name='t_box' AND index_name='idx_box_scope') OR
      (table_name='t_label' AND index_name='idx_label_scope') OR
      (table_name='t_print_job' AND index_name IN ('idx_print_scope','idx_print_scope_created')) OR
      (table_name='t_production_plan' AND index_name='idx_plan_factory') OR
      (table_name='t_material_demand' AND index_name='idx_demand_factory') OR
      (table_name='t_purchase_requirement' AND index_name='idx_purchase_factory') OR
      (table_name='log_scan' AND index_name='idx_scan_scope') OR
      (table_name='sys_outbox_event' AND index_name='idx_outbox_scope') OR
      (table_name='t_replenishment_task' AND index_name IN ('idx_task_scope_created','idx_task_scope_status_created')) OR
      (table_name='log_user_scope' AND index_name IN ('idx_user_scope_audit_target_created','idx_user_scope_audit_created'))
    )) AS datascope_index_count,
  (SELECT COUNT(*) FROM log_user_scope) AS user_scope_audit_row_count;

SELECT u.username, u.role, u.factory, u.enabled, COUNT(uda.id) AS delivery_area_count
FROM sys_user u
LEFT JOIN sys_user_delivery_area uda ON uda.user_id=u.id
WHERE u.username IN ('500001','500002','500003','500004','500005')
GROUP BY u.id, u.username, u.role, u.factory, u.enabled
ORDER BY u.username;

SELECT
  SUM(factory IS NULL OR TRIM(factory)='') AS task_factory_blank,
  SUM(delivery_area IS NULL OR TRIM(delivery_area)='') AS task_area_blank
FROM t_replenishment_task;

SELECT
  SUM(factory IS NULL OR TRIM(factory)='') AS print_factory_blank,
  SUM(delivery_area IS NULL OR TRIM(delivery_area)='') AS print_area_blank
FROM t_print_job;
SQL
else
  echo "范围数据空值统计和索引计数：因完整 DataScope Schema 校验未通过而跳过。"
fi

backup_dir="${DATASCOPE_BACKUP_DIR:-/opt/apps/material-pull/db-backups}"
backup_max_age_hours="${DATASCOPE_BACKUP_MAX_AGE_HOURS:-24}"
case "$backup_max_age_hours" in
  ''|*[!0-9]*) echo "备份校验：DATASCOPE_BACKUP_MAX_AGE_HOURS 必须是非负整数" >&2; exit 2 ;;
esac
if [ -d "$backup_dir" ]; then
  latest_backup=$(find "$backup_dir" -maxdepth 1 -type f -name '*.sql.gz' -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -n 1 | cut -d ' ' -f 2- || true)
  if [ -n "$latest_backup" ] && gzip -t "$latest_backup"; then
    backup_epoch=$(stat -c '%Y' "$latest_backup")
    now_epoch=$(date +%s)
    backup_age_seconds=$((now_epoch - backup_epoch))
    backup_age_hours=$((backup_age_seconds / 3600))
    if [ "$backup_age_seconds" -le "$((backup_max_age_hours * 3600))" ]; then
      echo "备份校验：完整且在 ${backup_max_age_hours} 小时内（$(basename "$latest_backup")，约 ${backup_age_hours} 小时前）"
    else
      echo "备份校验：文件完整但已过期（$(basename "$latest_backup")，约 ${backup_age_hours} 小时前）；发布前必须创建并验证当次备份"
      [ "$preflight_exit" -ne 0 ] || preflight_exit=6
    fi
  else
    echo "备份校验：未找到可验证的 .sql.gz 备份；发布前必须创建并验证当次备份"
    [ "$preflight_exit" -ne 0 ] || preflight_exit=6
  fi
else
  echo "备份校验：目录不存在（$backup_dir）；发布前必须创建并验证当次备份"
  [ "$preflight_exit" -ne 0 ] || preflight_exit=6
fi

if [ "$preflight_exit" -ne 0 ]; then
  echo "未通过：以上为只读预检结果；未执行 Migration、账号写入或应用操作。" >&2
  exit "$preflight_exit"
fi
echo "通过：以上均为只读检查；未执行 Migration、账号写入或应用操作。"
