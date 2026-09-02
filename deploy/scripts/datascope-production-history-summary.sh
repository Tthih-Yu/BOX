#!/usr/bin/env sh
# 生产历史范围候选汇总：只读，仅输出聚合数量，不生成或回填逐条业务数据。
set -eu

: "${DATASCOPE_PRODUCTION_HISTORY_SUMMARY_CONFIRM:?请设置 DATASCOPE_PRODUCTION_HISTORY_SUMMARY_CONFIRM=I_UNDERSTAND_PRODUCTION_READ_ONLY_SUMMARY}"
if [ "$DATASCOPE_PRODUCTION_HISTORY_SUMMARY_CONFIRM" != "I_UNDERSTAND_PRODUCTION_READ_ONLY_SUMMARY" ]; then
  echo "拒绝执行：未完成生产历史汇总只读确认" >&2
  exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE="${DATASCOPE_ENV_FILE:-/etc/material-pull/material-pull.env}"
SUMMARY_SQL="$PROJECT_ROOT/backend/src/main/resources/db/mysql/DATASCOPE_history_audit_summary.sql"

[ -r "$ENV_FILE" ] || { echo "拒绝执行：无法读取生产环境文件 $ENV_FILE；请通过 sudo 运行。" >&2; exit 2; }
[ -r "$SUMMARY_SQL" ] || { echo "拒绝执行：找不到候选汇总 SQL：$SUMMARY_SQL" >&2; exit 2; }
if sed '/^[[:space:]]*--/d; /^[[:space:]]*$/d' "$SUMMARY_SQL" |
  grep -Eiq '^[[:space:]]*(INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE|CALL|LOAD|GRANT|REVOKE|SET)[[:space:]]'; then
  echo "拒绝执行：候选汇总 SQL 中检测到写操作或会话修改语句" >&2
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
trap 'unset MYSQL_PWD MYSQL_PASSWORD' EXIT HUP INT TERM

echo "== DataScope 生产历史候选汇总（只读）=="
echo "数据库：$MYSQL_DATABASE（主机与用户名已隐藏）"
mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
  --database="$MYSQL_DATABASE" --batch --raw --table --default-character-set=utf8mb4 \
  --init-command="SET SESSION TRANSACTION READ ONLY" < "$SUMMARY_SQL"
echo "完成：仅执行聚合 SELECT；未生成逐条报告、未执行 Migration、账号写入或历史回填。"
