#!/usr/bin/env sh
# 仅按顺序执行 V0.9.6～V0.9.10 的 Expand Schema；任一步失败立即退出。
set -eu

: "${DATASCOPE_PRODUCTION_MIGRATION_CONFIRM:?请设置 DATASCOPE_PRODUCTION_MIGRATION_CONFIRM=EXECUTE_V0_9_6_TO_V0_9_10}"
if [ "$DATASCOPE_PRODUCTION_MIGRATION_CONFIRM" != "EXECUTE_V0_9_6_TO_V0_9_10" ]; then
  echo "拒绝执行：未完成 V0.9.6～V0.9.10 批次确认" >&2
  exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE="${DATASCOPE_ENV_FILE:-/etc/material-pull/material-pull.env}"

set -- \
  'V0.9.6__datascope_printjob.sql:f41d7c7b21734013099055726114793693fcdbeda8b05af5be47e0ee72930c91' \
  'V0.9.7__datascope_plan_chain.sql:e31d1faf67b8151ce6e793e1c311338c7981b7a920ee47ed84ad82e163006c89' \
  'V0.9.8__datascope_logs_events.sql:71789c83283c8759f32294c9d3d4f6f24e49ad4b775d486e1eaaa05cffea1e20' \
  'V0.9.9__datascope_query_indexes.sql:d9964f4818b54e60c793b1fb2a9df615f2ed0c50ce33dd513af4da3f4ce6af25' \
  'V0.9.10__datascope_user_scope_audit.sql:42b9c5f2f5b8910c313872e8e9fa0ef9e8cee7682eb538a513d52bc1691085a0'
for item in "$@"; do
  file_name=${item%%:*}
  expected_sha=${item#*:}
  file_path="$PROJECT_ROOT/backend/src/main/resources/db/mysql/$file_name"
  [ -r "$file_path" ] || { echo "拒绝执行：找不到 $file_name" >&2; exit 2; }
  actual_sha=$(sha256sum "$file_path" | awk '{print $1}')
  [ "$actual_sha" = "$expected_sha" ] || { echo "拒绝执行：$file_name 校验值不匹配" >&2; exit 2; }
done

[ -r "$ENV_FILE" ] || { echo "拒绝执行：无法读取生产环境文件 $ENV_FILE；请通过 sudo 运行。" >&2; exit 2; }
set -a
. "$ENV_FILE"
set +a

MYSQL_USER="${MYSQL_USER:-${DB_USER:-${DB_USERNAME:-${SPRING_DATASOURCE_USERNAME:-}}}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}}"
MYSQL_HOST="${MYSQL_HOST:-${DB_HOST:-${DATABASE_HOST:-}}}"
MYSQL_PORT="${MYSQL_PORT:-${DB_PORT:-${DATABASE_PORT:-3306}}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-${DB_NAME:-${DB_DATABASE:-${DATABASE_NAME:-}}}}"
if [ -z "$MYSQL_HOST" ] || [ -z "$MYSQL_DATABASE" ]; then
  jdbc_url="${SPRING_DATASOURCE_URL:-${JDBC_DATABASE_URL:-${DATABASE_URL:-${DB_URL:-}}}}"
  case "$jdbc_url" in
    jdbc:mysql://*/*) endpoint="${jdbc_url#jdbc:mysql://}" ;;
    mysql://*/*) endpoint="${jdbc_url#mysql://}" ;;
    *) endpoint="" ;;
  esac
  if [ -n "$endpoint" ]; then
    authority="${endpoint%%/*}"
    database_part="${endpoint#*/}"
    [ -n "$MYSQL_HOST" ] || MYSQL_HOST="${authority%%:*}"
    case "$authority" in *:*) MYSQL_PORT="${authority##*:}" ;; esac
    [ -n "$MYSQL_DATABASE" ] || MYSQL_DATABASE="${database_part%%\?*}"
  fi
fi
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_DATABASE="${MYSQL_DATABASE:-material_pull}"
: "${MYSQL_USER:?环境文件未提供 MySQL 用户名}"
: "${MYSQL_PASSWORD:?环境文件未提供 MySQL 密码}"
command -v mysql >/dev/null 2>&1 || { echo "缺少 mysql 客户端" >&2; exit 127; }

export MYSQL_PWD="$MYSQL_PASSWORD"
trap 'unset MYSQL_PWD MYSQL_PASSWORD' EXIT HUP INT TERM
mysql_cmd() {
  mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
    --database="$MYSQL_DATABASE" --batch --raw "$@"
}
fail() { echo "批次停止：$1" >&2; exit 5; }

grant_text=$(mysql_cmd --skip-column-names -e "SHOW GRANTS FOR CURRENT_USER()")
case "$grant_text" in
  *"ALL PRIVILEGES"*) ;;
  *)
    for privilege in ALTER INDEX CREATE; do
      if ! printf '%s\n' "$grant_text" | grep -Eqi "(^|[ ,])$privilege([ ,]|$)"; then
        echo "拒绝执行：当前数据库用户缺少批次所需的 $privilege 权限。" >&2
        exit 3
      fi
    done
    ;;
esac

# 批次必须从 V0.9.5 完整状态开始，且 V0.9.6～V0.9.10 的对象均尚未存在。
factory_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_factory WHERE factory_code='弋江'")
area_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_delivery_area WHERE factory_code='弋江'")
[ "$factory_count" = "1" ] && [ "$area_count" = "17" ] || { echo "拒绝执行：V0.9.3 前置字典不完整。" >&2; exit 4; }
required_tables=$(mysql_cmd --skip-column-names -e "SELECT COALESCE(GROUP_CONCAT(e.table_name ORDER BY e.table_name SEPARATOR ','), '') FROM (
 SELECT 't_print_job' AS table_name UNION ALL SELECT 't_production_plan' UNION ALL
 SELECT 't_material_demand' UNION ALL SELECT 't_purchase_requirement' UNION ALL
 SELECT 'log_scan' UNION ALL SELECT 'sys_outbox_event' UNION ALL SELECT 't_replenishment_task'
) e LEFT JOIN information_schema.tables t ON t.table_schema=DATABASE() AND t.table_name=e.table_name WHERE t.table_name IS NULL")
[ -z "$required_tables" ] || { echo "拒绝执行：缺少批次所需表：$required_tables" >&2; exit 4; }
task_columns_missing=$(mysql_cmd --skip-column-names -e "SELECT COALESCE(GROUP_CONCAT(e.column_name ORDER BY e.column_name SEPARATOR ','), '') FROM (
 SELECT 'factory' AS column_name UNION ALL SELECT 'delivery_area' UNION ALL SELECT 'created_at' UNION ALL SELECT 'status'
) e LEFT JOIN information_schema.columns c ON c.table_schema=DATABASE() AND c.table_name='t_replenishment_task' AND c.column_name=e.column_name WHERE c.column_name IS NULL")
[ -z "$task_columns_missing" ] || { echo "拒绝执行：Task 缺少 V0.9.9 所需字段：$task_columns_missing" >&2; exit 4; }
existing=$(mysql_cmd --skip-column-names -e "
SELECT COALESCE(GROUP_CONCAT(item ORDER BY item SEPARATOR ','), '') FROM (
 SELECT CONCAT(table_name,'.',column_name) AS item FROM information_schema.columns
  WHERE table_schema=DATABASE() AND (
    (table_name='t_print_job' AND column_name='delivery_area') OR
    (table_name IN ('t_production_plan','t_material_demand','t_purchase_requirement') AND column_name='factory') OR
    (table_name='log_scan' AND column_name IN ('factory','delivery_area')) OR
    (table_name='sys_outbox_event' AND column_name IN ('scope_type','factory','delivery_area')))
 UNION ALL SELECT CONCAT(table_name,'.',index_name) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND (
    (table_name='t_print_job' AND index_name IN ('idx_print_scope','idx_print_scope_created')) OR
    (table_name='t_production_plan' AND index_name='idx_plan_factory') OR
    (table_name='t_material_demand' AND index_name='idx_demand_factory') OR
    (table_name='t_purchase_requirement' AND index_name='idx_purchase_factory') OR
    (table_name='log_scan' AND index_name='idx_scan_scope') OR
    (table_name='sys_outbox_event' AND index_name='idx_outbox_scope') OR
    (table_name='t_replenishment_task' AND index_name IN ('idx_task_scope_created','idx_task_scope_status_created')))
 UNION ALL SELECT 'log_user_scope' FROM information_schema.tables
  WHERE table_schema=DATABASE() AND table_name='log_user_scope'
) x")
if [ -n "$existing" ]; then
  echo "拒绝执行：批次前置状态不是全新状态，已存在：$existing。请人工核对，禁止重跑。" >&2
  exit 4
fi

table_counts=$(mysql_cmd --skip-column-names -e "SELECT CONCAT_WS(',',
 (SELECT COUNT(*) FROM t_print_job),
 (SELECT COUNT(*) FROM t_production_plan),
 (SELECT COUNT(*) FROM t_material_demand),
 (SELECT COUNT(*) FROM t_purchase_requirement),
 (SELECT COUNT(*) FROM log_scan),
 (SELECT COUNT(*) FROM sys_outbox_event),
 (SELECT COUNT(*) FROM t_replenishment_task))")
IFS=, read -r print_rows plan_rows demand_rows purchase_rows scan_rows outbox_rows task_rows <<EOF
$table_counts
EOF
echo "开始批次 V0.9.6～V0.9.10：print=$print_rows plan=$plan_rows demand=$demand_rows purchase=$purchase_rows scan=$scan_rows outbox=$outbox_rows task=$task_rows。"

run_sql() {
  version=$1
  file_name=$2
  echo "执行 $version..."
  mysql_cmd < "$PROJECT_ROOT/backend/src/main/resources/db/mysql/$file_name"
}

run_sql V0.9.6 V0.9.6__datascope_printjob.sql
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_print_job' AND column_name='delivery_area'")" = "1" ] || fail "V0.9.6 delivery_area 缺失"
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_print_job' AND index_name='idx_print_scope'")" = "1" ] || fail "V0.9.6 idx_print_scope 缺失"
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM t_print_job WHERE delivery_area IS NOT NULL")" = "0" ] || fail "V0.9.6 意外写入历史 PrintJob 范围"

run_sql V0.9.7 V0.9.7__datascope_plan_chain.sql
for table_name in t_production_plan t_material_demand t_purchase_requirement; do
  [ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='$table_name' AND column_name='factory'")" = "1" ] || fail "V0.9.7 $table_name.factory 缺失"
  [ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM $table_name WHERE factory IS NOT NULL")" = "0" ] || fail "V0.9.7 意外写入 $table_name 历史范围"
done
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND ((table_name='t_production_plan' AND index_name='idx_plan_factory') OR (table_name='t_material_demand' AND index_name='idx_demand_factory') OR (table_name='t_purchase_requirement' AND index_name='idx_purchase_factory'))")" = "3" ] || fail "V0.9.7 范围索引缺失"

run_sql V0.9.8 V0.9.8__datascope_logs_events.sql
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='log_scan' AND column_name IN ('factory','delivery_area')")" = "2" ] || fail "V0.9.8 ScanLog 字段缺失"
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_outbox_event' AND column_name IN ('scope_type','factory','delivery_area')")" = "3" ] || fail "V0.9.8 Outbox 字段缺失"
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND ((table_name='log_scan' AND index_name='idx_scan_scope') OR (table_name='sys_outbox_event' AND index_name='idx_outbox_scope'))")" = "2" ] || fail "V0.9.8 范围索引缺失"
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM log_scan WHERE factory IS NOT NULL OR delivery_area IS NOT NULL")" = "0" ] || fail "V0.9.8 意外写入历史 ScanLog 范围"
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_outbox_event WHERE scope_type IS NOT NULL OR factory IS NOT NULL OR delivery_area IS NOT NULL")" = "0" ] || fail "V0.9.8 意外写入历史 Outbox 范围"

run_sql V0.9.9 V0.9.9__datascope_query_indexes.sql
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_replenishment_task' AND index_name IN ('idx_task_scope_created','idx_task_scope_status_created')")" = "2" ] || fail "V0.9.9 Task 索引缺失"
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_print_job' AND index_name='idx_print_scope_created'")" = "1" ] || fail "V0.9.9 PrintJob 索引缺失"

run_sql V0.9.10 V0.9.10__datascope_user_scope_audit.sql
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='log_user_scope'")" = "1" ] || fail "V0.9.10 审计表缺失"
[ "$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM log_user_scope")" = "0" ] || fail "V0.9.10 审计表创建后不应已有记录"

backup_dir="${DATASCOPE_BACKUP_DIR:-/opt/apps/material-pull/db-backups}"
receipt="$backup_dir/datascope-v0.9.6-v0.9.10-$(date +%Y%m%d-%H%M%S).receipt.txt"
umask 077
{
  echo "migrations=V0.9.6,V0.9.7,V0.9.8,V0.9.9,V0.9.10"
  echo "v096_sha256=f41d7c7b21734013099055726114793693fcdbeda8b05af5be47e0ee72930c91"
  echo "v097_sha256=e31d1faf67b8151ce6e793e1c311338c7981b7a920ee47ed84ad82e163006c89"
  echo "v098_sha256=71789c83283c8759f32294c9d3d4f6f24e49ad4b775d486e1eaaa05cffea1e20"
  echo "v099_sha256=d9964f4818b54e60c793b1fb2a9df615f2ed0c50ce33dd513af4da3f4ce6af25"
  echo "v0910_sha256=42b9c5f2f5b8910c313872e8e9fa0ef9e8cee7682eb538a513d52bc1691085a0"
  echo "database=$MYSQL_DATABASE"
  echo "rows_before=print:$print_rows,plan:$plan_rows,demand:$demand_rows,purchase:$purchase_rows,scan:$scan_rows,outbox:$outbox_rows,task:$task_rows"
  echo "completed_at=$(date -Is)"
} > "$receipt"
chmod 600 "$receipt"

echo "V0.9.6～V0.9.10 批次执行并逐项验证通过；未创建账号、未回填历史范围。"
echo "执行回执：$receipt"
