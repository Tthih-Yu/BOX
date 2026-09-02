#!/usr/bin/env sh
# 只执行 V0.9.4：Inventory Expand Schema。禁止用于其他 Migration。
set -eu

: "${DATASCOPE_PRODUCTION_MIGRATION_CONFIRM:?请设置 DATASCOPE_PRODUCTION_MIGRATION_CONFIRM=EXECUTE_V0_9_4}"
if [ "$DATASCOPE_PRODUCTION_MIGRATION_CONFIRM" != "EXECUTE_V0_9_4" ]; then
  echo "拒绝执行：未完成 V0.9.4 生产变更确认" >&2
  exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
MIGRATION_SQL="$PROJECT_ROOT/backend/src/main/resources/db/mysql/V0.9.4__datascope_inventory.sql"
EXPECTED_SHA256='c199cf013090b4d971baf8560bf9a38e2150b770b82ef8d2fef0bb431149573e'
ENV_FILE="${DATASCOPE_ENV_FILE:-/etc/material-pull/material-pull.env}"

[ -r "$ENV_FILE" ] || { echo "拒绝执行：无法读取生产环境文件 $ENV_FILE；请通过 sudo 运行。" >&2; exit 2; }
[ -r "$MIGRATION_SQL" ] || { echo "拒绝执行：找不到 V0.9.4 SQL 文件" >&2; exit 2; }
actual_sha256=$(sha256sum "$MIGRATION_SQL" | awk '{print $1}')
[ "$actual_sha256" = "$EXPECTED_SHA256" ] || { echo "拒绝执行：V0.9.4 SQL 校验值不匹配" >&2; exit 2; }

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

grant_text=$(mysql_cmd --skip-column-names -e "SHOW GRANTS FOR CURRENT_USER()")
case "$grant_text" in
  *"ALL PRIVILEGES"*) ;;
  *)
    for privilege in ALTER INDEX; do
      if ! printf '%s\n' "$grant_text" | grep -Eqi "(^|[ ,])$privilege([ ,]|$)"; then
        echo "拒绝执行：当前数据库用户缺少 V0.9.4 所需的 $privilege 权限。" >&2
        exit 3
      fi
    done
    ;;
esac

# V0.9.4 依赖 V0.9.3 字典；自身 DDL 不是幂等 SQL，任一新增字段/索引已存在则拒绝重跑。
factory_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_factory WHERE factory_code='弋江'")
area_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_delivery_area WHERE factory_code='弋江'")
if [ "$factory_count" != "1" ] || [ "$area_count" != "17" ]; then
  echo "拒绝执行：V0.9.3 前置字典不完整（factory=$factory_count areas=$area_count）。" >&2
  exit 4
fi
existing=$(mysql_cmd --skip-column-names -e "
SELECT COALESCE(GROUP_CONCAT(item ORDER BY item SEPARATOR ','), '') FROM (
 SELECT column_name AS item FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='t_inventory'
    AND column_name IN ('factory','delivery_area','scope_location_material_hash')
 UNION ALL SELECT index_name FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='t_inventory'
    AND index_name IN ('idx_inventory_scope','uk_inventory_scope_location_material')
) x")
if [ -n "$existing" ]; then
  echo "拒绝执行：V0.9.4 前置状态不是全新状态，已存在：$existing。请人工核对，禁止重跑。" >&2
  exit 4
fi

inventory_rows=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM t_inventory")
inventory_bytes=$(mysql_cmd --skip-column-names -e "SELECT COALESCE(data_length + index_length, 0) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='t_inventory'")
echo "开始执行 V0.9.4：数据库=$MYSQL_DATABASE，inventory_rows=$inventory_rows，inventory_bytes=$inventory_bytes。"
mysql_cmd < "$MIGRATION_SQL"

post_missing=$(mysql_cmd --skip-column-names -e "
WITH expected(item) AS (
 SELECT 'factory' UNION ALL SELECT 'delivery_area' UNION ALL SELECT 'scope_location_material_hash' UNION ALL
 SELECT 'idx_inventory_scope' UNION ALL SELECT 'uk_inventory_scope_location_material'
), present(item) AS (
 SELECT column_name FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='t_inventory'
    AND column_name IN ('factory','delivery_area','scope_location_material_hash')
 UNION ALL SELECT index_name FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='t_inventory'
    AND index_name IN ('idx_inventory_scope','uk_inventory_scope_location_material')
)
SELECT COALESCE(GROUP_CONCAT(e.item ORDER BY e.item SEPARATOR ','), '')
FROM expected e LEFT JOIN present p ON p.item=e.item WHERE p.item IS NULL")
[ -z "$post_missing" ] || { echo "执行后验证失败，缺少：$post_missing；请立即停止后续 Migration。" >&2; exit 5; }

scoped_history_rows=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM t_inventory WHERE factory IS NOT NULL OR delivery_area IS NOT NULL")
[ "$scoped_history_rows" = "0" ] || { echo "执行后验证失败：发现 $scoped_history_rows 条历史库存范围被意外写入。" >&2; exit 5; }

backup_dir="${DATASCOPE_BACKUP_DIR:-/opt/apps/material-pull/db-backups}"
receipt="$backup_dir/datascope-v0.9.4-$(date +%Y%m%d-%H%M%S).receipt.txt"
umask 077
{
  echo "migration=V0.9.4__datascope_inventory.sql"
  echo "sha256=$actual_sha256"
  echo "database=$MYSQL_DATABASE"
  echo "inventory_rows_before=$inventory_rows"
  echo "inventory_bytes_before=$inventory_bytes"
  echo "historical_scoped_rows_after=$scoped_history_rows"
  echo "completed_at=$(date -Is)"
} > "$receipt"
chmod 600 "$receipt"

echo "V0.9.4 执行并验证通过：历史库存范围仍全部为空。"
echo "执行回执：$receipt"
