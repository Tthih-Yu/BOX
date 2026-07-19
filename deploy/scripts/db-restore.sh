#!/usr/bin/env sh
# 数据库恢复脚本（MySQL）。用法：
#   MYSQL_PASSWORD=xxx ./db-restore.sh /backups/material_pull-20260629-120000.sql.gz
set -eu

DUMP_FILE="${1:?用法：db-restore.sh <备份文件.sql.gz>}"
: "${MYSQL_HOST:=mysql}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_DATABASE:=material_pull}"
: "${MYSQL_USER:=material_pull}"
: "${MYSQL_PASSWORD:?需要设置 MYSQL_PASSWORD}"

echo "[restore] 即将把 $DUMP_FILE 恢复到 $MYSQL_DATABASE，这会覆盖现有数据。"
echo "[restore] 5 秒内 Ctrl-C 取消..."
sleep 5

gunzip -c "$DUMP_FILE" | mysql \
  --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" \
  --default-character-set=utf8mb4 \
  "$MYSQL_DATABASE"

echo "[restore] 完成"
