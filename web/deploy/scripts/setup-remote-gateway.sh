#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$SCRIPT_DIR/material-pull.env"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

ALIYUN_IP="${TUNNEL_REMOTE_HOST:-47.103.94.109}"
ALIYUN_USER="${TUNNEL_REMOTE_USER:-root}"
SSH_KEY="${TUNNEL_SSH_KEY:-$HOME/.ssh/aliyun_key.pem}"
CONF_SRC="$PROJECT_ROOT/deploy/nginx/aliyun-public-gateway.conf"
REMOTE_CONF="/etc/nginx/sites-available/material-pull-public.conf"

if [[ ! -f "$SSH_KEY" ]]; then
  echo "未找到 SSH 私钥: $SSH_KEY"
  exit 1
fi

chmod 600 "$SSH_KEY"

echo "上传远端 Nginx 配置到 ${ALIYUN_USER}@${ALIYUN_IP} ..."

scp -i "$SSH_KEY" \
  -o StrictHostKeyChecking=accept-new \
  "$CONF_SRC" "${ALIYUN_USER}@${ALIYUN_IP}:/tmp/material-pull-public.conf"

ssh -i "$SSH_KEY" \
  -o StrictHostKeyChecking=accept-new \
  "${ALIYUN_USER}@${ALIYUN_IP}" bash -s <<'REMOTE'
set -euo pipefail
sudo mv /tmp/material-pull-public.conf /etc/nginx/sites-available/material-pull-public.conf
sudo ln -sfn /etc/nginx/sites-available/material-pull-public.conf /etc/nginx/sites-enabled/material-pull-public.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
echo "远端 Nginx 已切换为物料拉动公网入口"
REMOTE

echo
echo "公网访问地址: http://${ALIYUN_IP}/"
