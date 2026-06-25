#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)"
ENV_FILE="$SCRIPT_DIR/material-pull.env"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

ALIYUN_IP="${TUNNEL_REMOTE_HOST:-47.103.94.109}"
ALIYUN_USER="${TUNNEL_REMOTE_USER:-root}"
LOCAL_PORT="${TUNNEL_LOCAL_PORT:-8888}"
REMOTE_PORT="${TUNNEL_REMOTE_PORT:-18080}"
SSH_KEY="${TUNNEL_SSH_KEY:-$HOME/.ssh/aliyun_key.pem}"
LOG_FILE="${TUNNEL_LOG_FILE:-$HOME/.ssh/tunnel.log}"

if [[ ! -f "$SSH_KEY" ]]; then
  echo "未找到 SSH 私钥: $SSH_KEY"
  exit 1
fi

chmod 600 "$SSH_KEY" 2>/dev/null || true

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

log "启动物料拉动系统 SSH 反向隧道"
log "本地端口: $LOCAL_PORT -> 远端 127.0.0.1:$REMOTE_PORT"
log "阿里云: ${ALIYUN_USER}@${ALIYUN_IP}"

while true; do
  log "建立 SSH 隧道..."

  if ssh -i "$SSH_KEY" \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -o ExitOnForwardFailure=yes \
    -o StrictHostKeyChecking=accept-new \
    -R "127.0.0.1:${REMOTE_PORT}:127.0.0.1:${LOCAL_PORT}" \
    "${ALIYUN_USER}@${ALIYUN_IP}" \
    -N; then
    log "隧道正常退出"
  else
    log "隧道断开 (退出码: $?)，10 秒后重连..."
  fi

  sleep 10
done
