#!/usr/bin/env sh
# 数据库备份脚本。支持 MySQL（mysqldump）与 H2（文件复制）两种模式。
# 用法：
#   BACKUP_MODE=mysql ./db-backup.sh
#   BACKUP_MODE=h2    ./db-backup.sh
# 可被 cron 或 compose 中的备份容器周期调用。
set -eu

BACKUP_MODE="${BACKUP_MODE:-mysql}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"

case "$BACKUP_MODE" in
  mysql)
    : "${MYSQL_HOST:=mysql}"
    : "${MYSQL_PORT:=3306}"
    : "${MYSQL_DATABASE:=material_pull}"
    : "${MYSQL_USER:=material_pull}"
    : "${MYSQL_PASSWORD:?需要设置 MYSQL_PASSWORD}"
    OUT="$BACKUP_DIR/material_pull-$TIMESTAMP.sql.gz"
    echo "[backup] mysqldump $MYSQL_DATABASE -> $OUT"
    mysqldump \
      --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
      --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" \
      --single-transaction --quick --routines --triggers --events \
      --default-character-set=utf8mb4 \
      "$MYSQL_DATABASE" | gzip > "$OUT"
    ;;
  h2)
    : "${H2_DATA_DIR:=/app/data}"
    OUT="$BACKUP_DIR/h2-data-$TIMESTAMP.tar.gz"
    echo "[backup] H2 data dir $H2_DATA_DIR -> $OUT"
    tar czf "$OUT" -C "$H2_DATA_DIR" .
    ;;
  *)
    echo "未知 BACKUP_MODE=$BACKUP_MODE（应为 mysql 或 h2）" >&2
    exit 1
    ;;
esac

echo "[backup] 清理 $RETENTION_DAYS 天前的旧备份"
find "$BACKUP_DIR" -type f -name '*.gz' -mtime +"$RETENTION_DAYS" -delete 2>/dev/null || true
echo "[backup] 完成：$OUT"
