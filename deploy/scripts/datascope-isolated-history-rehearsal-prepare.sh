#!/usr/bin/env sh
# 从已校验备份创建新隔离演练库，并执行已发布的 DataScope Expand Schema。
set -eu

: "${DATASCOPE_ISOLATED_REHEARSAL_CONFIRM:?请设置 DATASCOPE_ISOLATED_REHEARSAL_CONFIRM=PREPARE_NEW_ISOLATED_DATABASE}"
[ "$DATASCOPE_ISOLATED_REHEARSAL_CONFIRM" = "PREPARE_NEW_ISOLATED_DATABASE" ] || {
  echo "拒绝执行：未完成新隔离演练库准备确认" >&2; exit 2;
}
: "${DATASCOPE_REHEARSAL_DATABASE:?请设置 DATASCOPE_REHEARSAL_DATABASE}"
: "${DATASCOPE_REHEARSAL_SOURCE_BACKUP:?请设置 DATASCOPE_REHEARSAL_SOURCE_BACKUP}"
case "$DATASCOPE_REHEARSAL_DATABASE" in
  material_pull_datascope_rehearsal_*) ;;
  *) echo "拒绝执行：数据库名必须以 material_pull_datascope_rehearsal_ 开头" >&2; exit 2 ;;
esac
case "$DATASCOPE_REHEARSAL_DATABASE" in
  *[!A-Za-z0-9_]*) echo "拒绝执行：数据库名只能使用字母、数字和下划线" >&2; exit 2 ;;
esac

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE="${DATASCOPE_ENV_FILE:-/etc/material-pull/material-pull.env}"
[ -r "$ENV_FILE" ] || { echo "拒绝执行：无法读取环境文件 $ENV_FILE；请通过 sudo 运行。" >&2; exit 2; }
[ -r "$DATASCOPE_REHEARSAL_SOURCE_BACKUP" ] || { echo "拒绝执行：无法读取备份" >&2; exit 2; }
gzip -t "$DATASCOPE_REHEARSAL_SOURCE_BACKUP" || { echo "拒绝执行：备份 gzip 校验失败" >&2; exit 3; }

set -a
. "$ENV_FILE"
set +a
MYSQL_USER="${MYSQL_USER:-${DB_USER:-${DB_USERNAME:-${SPRING_DATASOURCE_USERNAME:-}}}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}}"
MYSQL_HOST="${MYSQL_HOST:-${DB_HOST:-${DATABASE_HOST:-}}}"
MYSQL_PORT="${MYSQL_PORT:-${DB_PORT:-${DATABASE_PORT:-3306}}}"
if [ -z "$MYSQL_HOST" ]; then
  jdbc_url="${SPRING_DATASOURCE_URL:-${JDBC_DATABASE_URL:-${DATABASE_URL:-${DB_URL:-}}}}"
  case "$jdbc_url" in
    jdbc:mysql://*/*) endpoint="${jdbc_url#jdbc:mysql://}" ;;
    mysql://*/*) endpoint="${jdbc_url#mysql://}" ;;
    *) endpoint="" ;;
  esac
  if [ -n "$endpoint" ]; then
    authority="${endpoint%%/*}"
    MYSQL_HOST="${authority%%:*}"
    case "$authority" in *:*) MYSQL_PORT="${authority##*:}" ;; esac
  fi
fi
MYSQL_HOST="${MYSQL_HOST:-localhost}"
: "${MYSQL_USER:?环境文件未提供 MySQL 用户名}"
: "${MYSQL_PASSWORD:?环境文件未提供 MySQL 密码}"
command -v mysql >/dev/null 2>&1 || { echo "缺少 mysql 客户端" >&2; exit 127; }

export MYSQL_PWD="$MYSQL_PASSWORD"
trap 'unset MYSQL_PWD MYSQL_PASSWORD' EXIT HUP INT TERM
mysql_cmd() {
  mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
    --database="$DATASCOPE_REHEARSAL_DATABASE" --batch --raw "$@"
}

table_count=$(mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
  --database=information_schema --batch --skip-column-names \
  -e "SELECT COUNT(*) FROM tables WHERE table_schema='$DATASCOPE_REHEARSAL_DATABASE'")
[ "$table_count" = "0" ] || {
  echo "拒绝执行：目标隔离库已有 $table_count 张表，禁止覆盖。" >&2; exit 4;
}

echo "开始建立隔离演练库：数据库=$DATASCOPE_REHEARSAL_DATABASE，源备份=$(basename "$DATASCOPE_REHEARSAL_SOURCE_BACKUP")"
gzip -cd "$DATASCOPE_REHEARSAL_SOURCE_BACKUP" | mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
  --default-character-set=utf8mb4 "$DATASCOPE_REHEARSAL_DATABASE"

baseline_columns=$(mysql_cmd --skip-column-names -e "
SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND
  ((table_name='m_material_mapping' AND column_name='factory') OR
   (table_name='t_replenishment_task' AND column_name='factory') OR
   (table_name='t_print_job' AND column_name='factory'))")
[ "$baseline_columns" = "3" ] || { echo "拒绝继续：备份缺少 V0.9.1/V0.9.2 前置字段。" >&2; exit 4; }

set -- \
  'V0.9.3__datascope_user_scope.sql:05efcf0a44388439149b5d17d990581a7db5aea2fe9c8c35f313d8ef4e106730' \
  'V0.9.4__datascope_inventory.sql:c199cf013090b4d971baf8560bf9a38e2150b770b82ef8d2fef0bb431149573e' \
  'V0.9.5__datascope_box_label.sql:a4728bfaa5ea4fac9856b006de897cf408ec4dd0f66fbd852cd8236ad7c4109c' \
  'V0.9.6__datascope_printjob.sql:f41d7c7b21734013099055726114793693fcdbeda8b05af5be47e0ee72930c91' \
  'V0.9.7__datascope_plan_chain.sql:e31d1faf67b8151ce6e793e1c311338c7981b7a920ee47ed84ad82e163006c89' \
  'V0.9.8__datascope_logs_events.sql:71789c83283c8759f32294c9d3d4f6f24e49ad4b775d486e1eaaa05cffea1e20' \
  'V0.9.9__datascope_query_indexes.sql:d9964f4818b54e60c793b1fb2a9df615f2ed0c50ce33dd513af4da3f4ce6af25' \
  'V0.9.10__datascope_user_scope_audit.sql:42b9c5f2f5b8910c313872e8e9fa0ef9e8cee7682eb538a513d52bc1691085a0'
for item in "$@"; do
  file_name=${item%%:*}
  expected_sha=${item#*:}
  migration="$PROJECT_ROOT/backend/src/main/resources/db/mysql/$file_name"
  [ -r "$migration" ] || { echo "拒绝继续：缺少 $file_name" >&2; exit 4; }
  actual_sha=$(sha256sum "$migration" | awk '{print $1}')
  [ "$actual_sha" = "$expected_sha" ] || { echo "拒绝继续：$file_name 哈希不匹配" >&2; exit 4; }
  echo "执行隔离 $file_name..."
  mysql_cmd < "$migration"
done

factory_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_factory WHERE factory_code='弋江'")
area_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_delivery_area WHERE factory_code='弋江'")
index_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name IN ('uk_user_area','idx_user_id','idx_delivery_area','uk_factory_area','idx_factory','idx_inventory_scope','uk_inventory_scope_location_material','idx_box_scope','idx_label_scope','idx_print_scope','idx_plan_factory','idx_demand_factory','idx_purchase_factory','idx_scan_scope','idx_outbox_scope','idx_task_scope_created','idx_task_scope_status_created','idx_print_scope_created','idx_user_scope_audit_target_created','idx_user_scope_audit_created')")
audit_table_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='log_user_scope'")
[ "$factory_count" = "1" ] && [ "$area_count" = "17" ] && [ "$index_count" = "20" ] && [ "$audit_table_count" = "1" ] || {
  echo "隔离库结构核验失败：factory=$factory_count areas=$area_count indexes=$index_count audit_table=$audit_table_count" >&2; exit 5;
}

task_rows=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM t_replenishment_task")
task_missing_factory=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM t_replenishment_task WHERE factory IS NULL OR TRIM(factory)='' ")
print_rows=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM t_print_job")
print_missing_area=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM t_print_job WHERE delivery_area IS NULL OR TRIM(delivery_area)='' ")
result_dir="${DATASCOPE_REHEARSAL_RESULT_DIR:-$PROJECT_ROOT/.runtime/datascope-test-results}"
mkdir -p "$result_dir"
receipt="$result_dir/history-rehearsal-prepare-$(date +%Y%m%d-%H%M%S).txt"
umask 077
{
  echo "database=$DATASCOPE_REHEARSAL_DATABASE"
  echo "source_backup=$(basename "$DATASCOPE_REHEARSAL_SOURCE_BACKUP")"
  echo "factory_count=$factory_count"
  echo "delivery_area_count=$area_count"
  echo "datascope_index_count=$index_count"
  echo "task_rows=$task_rows"
  echo "task_missing_factory=$task_missing_factory"
  echo "print_rows=$print_rows"
  echo "print_missing_delivery_area=$print_missing_area"
  echo "completed_at=$(date -Is)"
} > "$receipt"
chmod 600 "$receipt"

echo "隔离演练库准备并验证通过：task=$task_rows（缺 factory=$task_missing_factory），print=$print_rows（缺 deliveryArea=$print_missing_area）。"
echo "回执：$receipt"
