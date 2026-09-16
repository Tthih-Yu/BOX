#!/usr/bin/env bash
# 安装并立即验证料号映射每日备份 systemd timer。
set -Eeuo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "请使用 sudo 运行：sudo $0" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
RUN_USER="${MAPPING_BACKUP_RUN_USER:-tthih}"
RUN_GROUP="${MAPPING_BACKUP_RUN_GROUP:-tthih}"
BACKUP_DIR="$PROJECT_ROOT/db-backups/mappings"
SERVICE_SOURCE="$PROJECT_ROOT/deploy/systemd/material-pull-mapping-backup.service"
TIMER_SOURCE="$PROJECT_ROOT/deploy/systemd/material-pull-mapping-backup.timer"

if [[ "$PROJECT_ROOT" != "/opt/apps/material-pull" ]]; then
  echo "当前 systemd 单元按 /opt/apps/material-pull 部署，实际目录为 $PROJECT_ROOT，拒绝安装。" >&2
  exit 2
fi
getent passwd "$RUN_USER" >/dev/null || { echo "运行用户不存在：$RUN_USER" >&2; exit 2; }
getent group "$RUN_GROUP" >/dev/null || { echo "运行组不存在：$RUN_GROUP" >&2; exit 2; }
[[ -r /etc/material-pull/material-pull.env ]] || {
  echo "生产环境文件不存在或不可读：/etc/material-pull/material-pull.env" >&2
  exit 2
}

install -d -m 0750 -o "$RUN_USER" -g "$RUN_GROUP" "$BACKUP_DIR"
touch "$BACKUP_DIR/.material-mapping-backup.lock"
chown "$RUN_USER:$RUN_GROUP" "$BACKUP_DIR/.material-mapping-backup.lock"
chmod 0600 "$BACKUP_DIR/.material-mapping-backup.lock"
install -m 0644 "$SERVICE_SOURCE" /etc/systemd/system/material-pull-mapping-backup.service
install -m 0644 "$TIMER_SOURCE" /etc/systemd/system/material-pull-mapping-backup.timer

systemctl daemon-reload
systemctl enable --now material-pull-mapping-backup.timer
systemctl start material-pull-mapping-backup.service

echo "每日料号映射备份已安装，默认执行时间为 Asia/Shanghai 02:15（最多随机延迟 5 分钟）。"
systemctl --no-pager status material-pull-mapping-backup.service || true
systemctl --no-pager status material-pull-mapping-backup.timer || true
