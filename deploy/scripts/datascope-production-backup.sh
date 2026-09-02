#!/usr/bin/env sh
# 生产 DataScope 发布前备份：只读一致性导出，不执行 Migration 或删除旧备份。
set -eu

: "${DATASCOPE_PRODUCTION_BACKUP_CONFIRM:?请设置 DATASCOPE_PRODUCTION_BACKUP_CONFIRM=I_UNDERSTAND_PRODUCTION_BACKUP}"
if [ "$DATASCOPE_PRODUCTION_BACKUP_CONFIRM" != "I_UNDERSTAND_PRODUCTION_BACKUP" ]; then
  echo "拒绝执行：未完成生产备份确认" >&2
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
command -v mysqldump >/dev/null 2>&1 || { echo "缺少 mysqldump 客户端" >&2; exit 127; }

backup_dir="${DATASCOPE_BACKUP_DIR:-/opt/apps/material-pull/db-backups}"
timestamp=$(date +%Y%m%d-%H%M%S)
output="$backup_dir/material_pull-datascope-preflight-$timestamp.sql.gz"
temporary="$output.part"

umask 077
mkdir -p "$backup_dir"
if [ -e "$output" ] || [ -e "$temporary" ]; then
  echo "拒绝执行：备份目标已存在 $output" >&2
  exit 2
fi

export MYSQL_PWD="$MYSQL_PASSWORD"
trap 'rm -f "$temporary"; unset MYSQL_PWD MYSQL_PASSWORD' EXIT HUP INT TERM

database_bytes=$(mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
  --database="$MYSQL_DATABASE" --batch --skip-column-names \
  --init-command="SET SESSION TRANSACTION READ ONLY" \
  -e "SELECT COALESCE(SUM(data_length + index_length), 0) FROM information_schema.tables WHERE table_schema=DATABASE()")
available_bytes=$(df -Pk "$backup_dir" | awk 'NR==2 {printf "%.0f\n", $4 * 1024}')
required_bytes=$((database_bytes + (database_bytes / 5) + 104857600))
if [ "$available_bytes" -lt "$required_bytes" ]; then
  echo "拒绝执行：备份目录可用空间不足（需要至少 $required_bytes 字节，当前 $available_bytes 字节）" >&2
  exit 3
fi

echo "开始一致性只读备份：数据库=$MYSQL_DATABASE，目标=$(basename "$output")"
mysqldump --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
  --single-transaction --quick --routines --triggers --events --no-tablespaces \
  --default-character-set=utf8mb4 "$MYSQL_DATABASE" | gzip -c > "$temporary"
gzip -t "$temporary"
mv "$temporary" "$output"
sha256sum "$output" > "$output.sha256"
chmod 600 "$output" "$output.sha256"

echo "备份完成：$output"
echo "校验文件：$output.sha256"
