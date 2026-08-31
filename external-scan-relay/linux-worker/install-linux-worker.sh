#!/usr/bin/env bash
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "请使用 sudo 运行：sudo bash $0" >&2
  exit 1
fi

project_dir="/opt/apps/material-pull/external-scan-relay/linux-worker"
config_dir="/etc/material-pull-external-scan"
config_file="$config_dir/worker.env"
service_file="/etc/systemd/system/external-scan-worker.service"
relay_token="11111111111111111111111111111111"

test -f "$project_dir/worker.py"
test -f "$project_dir/external-scan-worker.service"

curl --fail-with-body --silent --show-error --max-time 8 \
  -H "X-Relay-Token: $relay_token" \
  http://10.243.111.152/aliyun-relay/health >/dev/null

if ! id material-pull-worker >/dev/null 2>&1; then
  useradd --system --home /nonexistent --shell /usr/sbin/nologin material-pull-worker
fi

install -d -o root -g material-pull-worker -m 0750 "$config_dir"

device_key="$(openssl rand -hex 32)"
tmp_env="$(mktemp)"
trap 'rm -f "$tmp_env"' EXIT

printf "%s\n" \
  "SCAN_RELAY_URL=http://10.243.111.152/aliyun-relay" \
  "SCAN_RELAY_TOKEN=$relay_token" \
  "SCAN_INTERNAL_API=http://127.0.0.1:8080/api" \
  "SCAN_INTERNAL_DEVICE_KEY=$device_key" \
  "SCAN_WORKER_ID=material-pull-linux-01" \
  "SCAN_LONG_POLL_SECONDS=15" \
  "SCAN_RELAY_TIMEOUT_SECONDS=20" \
  "SCAN_INTERNAL_TIMEOUT_SECONDS=20" \
  "SCAN_ERROR_RETRY_SECONDS=5" >"$tmp_env"

install -o root -g material-pull-worker -m 0640 "$tmp_env" "$config_file"
install -o root -g root -m 0644 "$project_dir/external-scan-worker.service" "$service_file"

systemctl daemon-reload
systemctl enable --now external-scan-worker.service
systemctl --no-pager --full status external-scan-worker.service

echo "外网扫码 Linux Worker 已安装并启动。"
