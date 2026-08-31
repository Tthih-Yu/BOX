#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR=/opt/aliyun-api-relay
DATA_DIR=/var/lib/aliyun-api-relay
CONFIG_DIR=/etc/aliyun-api-relay
ENV_FILE="$CONFIG_DIR/api.env"
SERVICE_FILE=/etc/systemd/system/aliyun-api-relay.service
NGINX_SITE=/etc/nginx/sites-available/tthih.top
NGINX_LINK=/etc/nginx/sites-enabled/tthih.top
SERVICE_USER=aliyun-api-relay

if [[ ${EUID} -ne 0 ]]; then
  echo "请使用 sudo 运行此脚本" >&2
  exit 1
fi

for required in server.py aliyun-api-relay.service nginx-api.conf; do
  if [[ ! -f "$SOURCE_DIR/$required" ]]; then
    echo "缺少部署文件: $SOURCE_DIR/$required" >&2
    exit 1
  fi
done

command -v python3 >/dev/null
command -v nginx >/dev/null
command -v openssl >/dev/null
python3 -m py_compile "$SOURCE_DIR/server.py"

if ! id "$SERVICE_USER" >/dev/null 2>&1; then
  useradd --system --home "$DATA_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
fi

install -d -o root -g root -m 755 "$APP_DIR"
install -d -o "$SERVICE_USER" -g "$SERVICE_USER" -m 750 "$DATA_DIR"
install -d -o root -g "$SERVICE_USER" -m 750 "$CONFIG_DIR"
install -o root -g root -m 755 "$SOURCE_DIR/server.py" "$APP_DIR/server.py"
install -o root -g root -m 644 "$SOURCE_DIR/aliyun-api-relay.service" "$SERVICE_FILE"

if [[ ! -f "$ENV_FILE" ]]; then
  umask 027
  worker_token="$(openssl rand -hex 32)"
  enrollment_key="$(openssl rand -hex 32)"
  {
    echo 'RELAY_API_HOST=127.0.0.1'
    echo 'RELAY_API_PORT=18082'
    echo 'RELAY_API_DB=/var/lib/aliyun-api-relay/relay.db'
    echo "RELAY_API_TOKEN=$worker_token"
    echo "RELAY_APP_ENROLLMENT_KEY=$enrollment_key"
    echo 'RELAY_API_MAX_BODY=65536'
    echo 'RELAY_API_RESULT_MAX_BODY=262144'
    echo 'RELAY_COMMAND_LEASE_SECONDS=30'
    echo 'RELAY_COMMAND_TTL_SECONDS=120'
    echo 'RELAY_COMMAND_MAX_ATTEMPTS=3'
    echo 'RELAY_MAX_LONG_POLL_SECONDS=15'
    echo 'RELAY_DEVICE_RATE_LIMIT=30'
  } > "$ENV_FILE"
  chown root:"$SERVICE_USER" "$ENV_FILE"
  chmod 640 "$ENV_FILE"
  unset worker_token enrollment_key
fi

if [[ -f "$NGINX_SITE" ]]; then
  backup="$NGINX_SITE.before-external-scan-$(date +%Y%m%d-%H%M%S)"
  cp -a "$NGINX_SITE" "$backup"
fi
install -o root -g root -m 644 "$SOURCE_DIR/nginx-api.conf" "$NGINX_SITE"
ln -sfn "$NGINX_SITE" "$NGINX_LINK"
if [[ -L /etc/nginx/sites-enabled/default ]]; then
  unlink /etc/nginx/sites-enabled/default
fi

systemctl daemon-reload
systemctl enable aliyun-api-relay.service >/dev/null
systemctl restart aliyun-api-relay.service
nginx -t
systemctl reload nginx

for attempt in {1..20}; do
  if curl --fail --silent http://127.0.0.1:18082/health >/dev/null; then
    break
  fi
  if [[ $attempt -eq 20 ]]; then
    echo "API 未在预期时间内就绪" >&2
    exit 1
  fi
  sleep 0.5
done
find "$DATA_DIR" -maxdepth 1 -type f -exec chmod 640 {} +
curl --fail --silent --show-error https://tthih.top/health >/dev/null

echo "部署完成：aliyun-api-relay 与 Nginx 均已通过健康检查。"
echo "真实Token保存在 $ENV_FILE，未输出到终端。"
