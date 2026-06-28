#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TUNNEL_SCRIPT="$SCRIPT_DIR/keep-tunnel-alive.sh"
ENV_FILE="$SCRIPT_DIR/material-pull.env"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

ALIYUN_IP="${TUNNEL_REMOTE_HOST:-47.103.94.109}"
LOCAL_PORT="${TUNNEL_LOCAL_PORT:-8888}"

pkill -f "deploy/scripts/keep-tunnel-alive.sh" 2>/dev/null || true
pkill -f "keep-tunnel-alive.sh" 2>/dev/null || true
sleep 1

if ! curl -fsS "http://127.0.0.1:${LOCAL_PORT}/" >/dev/null 2>&1; then
  echo "警告: 本地网关 http://127.0.0.1:${LOCAL_PORT} 暂不可达"
  echo "请先执行:"
  echo "  sudo bash $SCRIPT_DIR/setup-local-gateway.sh"
  echo "  sudo systemctl restart material-pull-backend-dev material-pull-frontend-dev"
fi

nohup bash "$TUNNEL_SCRIPT" >/dev/null 2>&1 &
echo "SSH 隧道已在后台启动 (PID: $!)"
echo "日志: tail -f ~/.ssh/tunnel.log"
echo "公网地址: http://${ALIYUN_IP}/"
echo "停止隧道: pkill -f keep-tunnel-alive.sh"
