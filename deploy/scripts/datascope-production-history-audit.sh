#!/usr/bin/env sh
# 生产历史范围候选审计：只读查询，生成受限本地报告；不执行回填、Migration 或应用操作。
set -eu

: "${DATASCOPE_PRODUCTION_HISTORY_AUDIT_CONFIRM:?请设置 DATASCOPE_PRODUCTION_HISTORY_AUDIT_CONFIRM=I_UNDERSTAND_PRODUCTION_READ_ONLY_AUDIT}"
if [ "$DATASCOPE_PRODUCTION_HISTORY_AUDIT_CONFIRM" != "I_UNDERSTAND_PRODUCTION_READ_ONLY_AUDIT" ]; then
  echo "拒绝执行：未完成生产历史候选只读审计确认" >&2
  exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE="${DATASCOPE_ENV_FILE:-/etc/material-pull/material-pull.env}"
AUDIT_SQL="$PROJECT_ROOT/backend/src/main/resources/db/mysql/DATASCOPE_history_audit_candidates.sql"

[ -r "$ENV_FILE" ] || { echo "拒绝执行：无法读取生产环境文件 $ENV_FILE；请通过 sudo 运行。" >&2; exit 2; }
[ -r "$AUDIT_SQL" ] || { echo "拒绝执行：找不到候选审计 SQL：$AUDIT_SQL" >&2; exit 2; }

# 候选 SQL 是受控只读资产；拒绝携带任意 DML、DDL 或会话修改的内容。
if sed '/^[[:space:]]*--/d; /^[[:space:]]*$/d' "$AUDIT_SQL" |
  grep -Eiq '^[[:space:]]*(INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE|CALL|LOAD|GRANT|REVOKE|SET)[[:space:]]'; then
  echo "拒绝执行：候选审计 SQL 中检测到写操作或会话修改语句" >&2
  exit 3
fi

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
trap 'rm -f "${temporary:-}"; unset MYSQL_PWD MYSQL_PASSWORD' EXIT HUP INT TERM
mysql_cmd() {
  mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
    --database="$MYSQL_DATABASE" --batch --raw --init-command="SET SESSION TRANSACTION READ ONLY" "$@"
}

# 避免在 Schema 不完整时产生不可信报告。
missing_tables=$(mysql_cmd --skip-column-names -e "
SELECT COALESCE(GROUP_CONCAT(e.table_name ORDER BY e.table_name SEPARATOR ','), '') FROM (
 SELECT 'm_material_mapping' AS table_name UNION ALL SELECT 't_replenishment_task' UNION ALL
 SELECT 't_print_job' UNION ALL SELECT 't_box' UNION ALL SELECT 't_label' UNION ALL
 SELECT 't_inventory' UNION ALL SELECT 't_production_plan' UNION ALL
 SELECT 't_material_demand' UNION ALL SELECT 't_purchase_requirement' UNION ALL
 SELECT 'log_scan' UNION ALL SELECT 'sys_outbox_event' UNION ALL
 SELECT 'sys_factory' UNION ALL SELECT 'sys_delivery_area'
) e LEFT JOIN information_schema.tables t
  ON t.table_schema=DATABASE() AND t.table_name=e.table_name
WHERE t.table_name IS NULL")
[ -z "$missing_tables" ] || { echo "拒绝执行：DataScope Schema 不完整，缺少表：$missing_tables" >&2; exit 4; }

output_dir="${DATASCOPE_HISTORY_AUDIT_DIR:-$PROJECT_ROOT/db-backups}"
timestamp=$(date +%Y%m%d-%H%M%S)
output="$output_dir/datascope-production-history-audit-$timestamp.txt"
temporary="$output.part"
umask 077
mkdir -p "$output_dir"
[ ! -e "$output" ] && [ ! -e "$temporary" ] || { echo "拒绝执行：审计输出已存在：$output" >&2; exit 2; }

echo "开始生产历史候选只读审计：数据库=$MYSQL_DATABASE，输出=$(basename "$output")"
mysql_cmd --default-character-set=utf8mb4 --table < "$AUDIT_SQL" > "$temporary"
[ -s "$temporary" ] || { echo "拒绝执行：审计结果为空，未保留输出文件" >&2; exit 5; }
mv "$temporary" "$output"
chmod 600 "$output"

echo "候选审计完成：$output"
echo "结果仅供候选、审批和验证环境演练；未执行 Migration、账号写入、历史回填或应用操作。"
