#!/usr/bin/env sh
# 在隔离 MySQL 验证库执行 DataScope 只读审计，并保存完整结果。
# 本脚本不会执行 Migration，也不会修改数据。
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
AUDIT_SQL="${DATASCOPE_AUDIT_SQL:-$PROJECT_ROOT/backend/src/main/resources/db/mysql/DATASCOPE_history_audit_candidates.sql}"

: "${DATASCOPE_AUDIT_CONFIRM:?请设置 DATASCOPE_AUDIT_CONFIRM=I_AM_USING_AN_ISOLATED_DATABASE}"
if [ "$DATASCOPE_AUDIT_CONFIRM" != "I_AM_USING_AN_ISOLATED_DATABASE" ]; then
  echo "拒绝执行：必须明确确认使用隔离验证库" >&2
  exit 2
fi

: "${MYSQL_HOST:?需要设置 MYSQL_HOST}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_DATABASE:?需要设置 MYSQL_DATABASE}"
: "${MYSQL_USER:?需要设置只读 MYSQL_USER}"
: "${MYSQL_PASSWORD:?需要设置 MYSQL_PASSWORD}"
: "${DATASCOPE_AUDIT_OUTPUT:?需要设置 DATASCOPE_AUDIT_OUTPUT 结果文件路径}"

if [ ! -f "$AUDIT_SQL" ]; then
  echo "审计 SQL 不存在：$AUDIT_SQL" >&2
  exit 2
fi

# 双重门禁：脚本只允许注释、SELECT、WITH；发现写操作关键字立即拒绝。
if sed '/^[[:space:]]*--/d; /^[[:space:]]*$/d' "$AUDIT_SQL" |
  grep -Eiq '^[[:space:]]*(INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE|CALL|LOAD|GRANT|REVOKE|SET)[[:space:]]'; then
  echo "拒绝执行：审计 SQL 中检测到写操作或会话修改语句" >&2
  exit 3
fi

OUTPUT_DIR=$(dirname -- "$DATASCOPE_AUDIT_OUTPUT")
if [ ! -d "$OUTPUT_DIR" ]; then
  echo "结果目录不存在：$OUTPUT_DIR" >&2
  exit 2
fi
if [ -e "$DATASCOPE_AUDIT_OUTPUT" ]; then
  echo "拒绝覆盖已有结果：$DATASCOPE_AUDIT_OUTPUT" >&2
  exit 2
fi

command -v mysql >/dev/null 2>&1 || {
  echo "未安装 mysql 客户端" >&2
  exit 127
}

export MYSQL_PWD="$MYSQL_PASSWORD"
trap 'unset MYSQL_PWD' EXIT HUP INT TERM

echo "[datascope-audit] host=$MYSQL_HOST port=$MYSQL_PORT database=$MYSQL_DATABASE user=$MYSQL_USER"
echo "[datascope-audit] output=$DATASCOPE_AUDIT_OUTPUT"
mysql \
  --host="$MYSQL_HOST" \
  --port="$MYSQL_PORT" \
  --user="$MYSQL_USER" \
  --database="$MYSQL_DATABASE" \
  --default-character-set=utf8mb4 \
  --init-command="SET SESSION TRANSACTION READ ONLY" \
  --table \
  < "$AUDIT_SQL" > "$DATASCOPE_AUDIT_OUTPUT"

echo "[datascope-audit] 完成；未执行任何写操作"
