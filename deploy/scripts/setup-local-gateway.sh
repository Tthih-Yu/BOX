#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONF_SRC="$PROJECT_ROOT/deploy/nginx/material-pull-local-gateway.conf"
CONF_DST="/etc/nginx/sites-available/material-pull-local-gateway.conf"

if [[ "$EUID" -ne 0 ]]; then
  echo "请使用 sudo 运行："
  echo "  sudo bash $0"
  exit 1
fi

if [[ ! -f "$CONF_SRC" ]]; then
  echo "未找到配置: $CONF_SRC"
  exit 1
fi

cp "$CONF_SRC" "$CONF_DST"
ln -sfn "$CONF_DST" /etc/nginx/sites-enabled/material-pull-local-gateway.conf

nginx -t
systemctl reload nginx

echo
echo "本地网关已启用: http://127.0.0.1:8888"
echo "请确认前端(5173)与后端(8080)已启动后再建立隧道。"
