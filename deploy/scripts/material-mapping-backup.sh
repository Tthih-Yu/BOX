#!/usr/bin/env bash
# 每日料号映射备份：仅导出 m_material_mapping，校验成功后原子落盘。
set -Eeuo pipefail

ENV_FILE="${MAPPING_BACKUP_ENV_FILE:-/etc/material-pull/material-pull.env}"
BACKUP_DIR="${MAPPING_BACKUP_DIR:-/opt/apps/material-pull/db-backups/mappings}"
RETENTION_DAYS="${MAPPING_BACKUP_RETENTION_DAYS:-365}"

if [[ -r "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

MYSQL_USER="${MYSQL_USER:-${DB_USER:-${DB_USERNAME:-${SPRING_DATASOURCE_USERNAME:-}}}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}}"
MYSQL_HOST="${MYSQL_HOST:-${DB_HOST:-${DATABASE_HOST:-}}}"
MYSQL_PORT="${MYSQL_PORT:-${DB_PORT:-${DATABASE_PORT:-3306}}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-${DB_NAME:-${DB_DATABASE:-${DATABASE_NAME:-}}}}"

if [[ -z "$MYSQL_HOST" || -z "$MYSQL_DATABASE" ]]; then
  jdbc_url="${SPRING_DATASOURCE_URL:-${JDBC_DATABASE_URL:-${DATABASE_URL:-${DB_URL:-}}}}"
  case "$jdbc_url" in
    jdbc:mysql://*/*) endpoint="${jdbc_url#jdbc:mysql://}" ;;
    mysql://*/*) endpoint="${jdbc_url#mysql://}" ;;
    *) endpoint="" ;;
  esac
  if [[ -n "$endpoint" ]]; then
    authority="${endpoint%%/*}"
    database_part="${endpoint#*/}"
    [[ -n "$MYSQL_HOST" ]] || MYSQL_HOST="${authority%%:*}"
    if [[ "$authority" == *:* ]]; then MYSQL_PORT="${authority##*:}"; fi
    [[ -n "$MYSQL_DATABASE" ]] || MYSQL_DATABASE="${database_part%%\?*}"
  fi
fi

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_DATABASE="${MYSQL_DATABASE:-material_pull}"
: "${MYSQL_USER:?环境文件未提供 MySQL 用户名}"
: "${MYSQL_PASSWORD:?环境文件未提供 MySQL 密码}"

if [[ ! "$RETENTION_DAYS" =~ ^[1-9][0-9]*$ ]]; then
  echo "备份保留天数必须是正整数：$RETENTION_DAYS" >&2
  exit 2
fi

# 清理逻辑只能作用于专用子目录中的 material_mapping-* 文件，拒绝宽泛目录。
case "$BACKUP_DIR" in
  ""|/|/opt|/opt/apps|/opt/apps/material-pull|/opt/apps/material-pull/db-backups)
    echo "拒绝使用过宽的备份目录：$BACKUP_DIR" >&2
    exit 2
    ;;
esac

for command_name in mysql mysqldump gzip sha256sum flock awk; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "缺少必需命令：$command_name" >&2
    exit 127
  }
done

umask 077
mkdir -p "$BACKUP_DIR"
[[ -d "$BACKUP_DIR" && -w "$BACKUP_DIR" ]] || {
  echo "备份目录不可写：$BACKUP_DIR" >&2
  exit 3
}

exec 9>"$BACKUP_DIR/.material-mapping-backup.lock"
if ! flock -n 9; then
  echo "已有料号映射备份任务正在运行，本次跳过。" >&2
  exit 4
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
created_at="$(date --iso-8601=seconds)"
output="$BACKUP_DIR/material_mapping-$timestamp.sql.gz"
checksum="$output.sha256"
manifest="$output.manifest.txt"
temporary="$output.part"
checksum_temporary="$checksum.part"
manifest_temporary="$manifest.part"

cleanup() {
  rm -f -- "$temporary" "$checksum_temporary" "$manifest_temporary"
  unset MYSQL_PWD MYSQL_PASSWORD
}
trap cleanup EXIT HUP INT TERM

for target in "$output" "$checksum" "$manifest" "$temporary" "$checksum_temporary" "$manifest_temporary"; do
  if [[ -e "$target" ]]; then
    echo "拒绝覆盖已有文件：$target" >&2
    exit 5
  fi
done

export MYSQL_PWD="$MYSQL_PASSWORD"
mysql_args=(
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USER"
  --database="$MYSQL_DATABASE"
  --default-character-set=utf8mb4
)

table_exists="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --init-command="SET SESSION TRANSACTION READ ONLY" \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='m_material_mapping'")"
if [[ "$table_exists" != "1" ]]; then
  echo "数据库中不存在 m_material_mapping，拒绝生成空备份。" >&2
  exit 6
fi

read -r source_rows min_id max_id max_updated_at < <(
  mysql "${mysql_args[@]}" --batch --skip-column-names \
    --init-command="SET SESSION TRANSACTION READ ONLY" \
    -e "SELECT COUNT(*), COALESCE(MIN(id),0), COALESCE(MAX(id),0), COALESCE(DATE_FORMAT(MAX(updated_at),'%Y-%m-%dT%H:%i:%s.%f'),'NULL') FROM m_material_mapping"
)

echo "开始备份料号映射：数据库=$MYSQL_DATABASE，行数=$source_rows，文件=$(basename "$output")"
mysqldump \
  --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  --user="$MYSQL_USER" \
  --single-transaction --quick --no-tablespaces --skip-lock-tables \
  --hex-blob --skip-extended-insert --default-character-set=utf8mb4 \
  "$MYSQL_DATABASE" m_material_mapping | gzip -c >"$temporary"

gzip -t "$temporary"
gzip -cd "$temporary" | awk '
  /^CREATE TABLE `m_material_mapping`/ { found=1 }
  END { exit(found ? 0 : 1) }
'
dump_rows="$(gzip -cd "$temporary" | awk '/^INSERT INTO `m_material_mapping` VALUES / { count++ } END { print count+0 }')"
if [[ "$dump_rows" != "$source_rows" ]]; then
  echo "备份行数校验失败：源表=$source_rows，备份=$dump_rows" >&2
  exit 7
fi

sha256="$(sha256sum "$temporary" | awk '{print $1}')"
printf '%s  %s\n' "$sha256" "$(basename "$output")" >"$checksum_temporary"
{
  printf 'format_version=1\n'
  printf 'created_at=%s\n' "$created_at"
  printf 'database=%s\n' "$MYSQL_DATABASE"
  printf 'table=m_material_mapping\n'
  printf 'rows=%s\n' "$source_rows"
  printf 'min_id=%s\n' "$min_id"
  printf 'max_id=%s\n' "$max_id"
  printf 'max_updated_at=%s\n' "$max_updated_at"
  printf 'sha256=%s\n' "$sha256"
} >"$manifest_temporary"

chmod 600 "$temporary" "$checksum_temporary" "$manifest_temporary"
# 主备份最后出现；看到 .sql.gz 即表示校验文件和清单已经就绪。
mv "$checksum_temporary" "$checksum"
mv "$manifest_temporary" "$manifest"
mv "$temporary" "$output"

# 仅清理专用目录中的本脚本产物，不触碰全库备份或其他文件。
find "$BACKUP_DIR" -maxdepth 1 -type f \
  \( -name 'material_mapping-*.sql.gz' \
     -o -name 'material_mapping-*.sql.gz.sha256' \
     -o -name 'material_mapping-*.sql.gz.manifest.txt' \) \
  -mtime "+$RETENTION_DAYS" -delete

echo "料号映射备份完成：$output"
echo "行数校验：$dump_rows；SHA-256：$sha256"
