#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${MATERIAL_PULL_ENV:-$SCRIPT_DIR/material-pull.env}"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
PID_DIR="${PID_DIR:-$PROJECT_ROOT/.run}"

show_status() {
  local name="$1"
  local pid_file="$PID_DIR/$name.pid"
  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "[$name] 运行中 PID=$(cat "$pid_file")"
  else
    echo "[$name] 未运行"
  fi
}

if systemctl list-unit-files material-pull-backend-dev.service >/dev/null 2>&1; then
  echo "=== systemd 服务 ==="
  systemctl is-active material-pull-backend-dev.service 2>/dev/null | xargs -I{} echo "material-pull-backend-dev: {}"
  systemctl is-active material-pull-frontend-dev.service 2>/dev/null | xargs -I{} echo "material-pull-frontend-dev: {}"
  echo
fi

echo "=== 手动脚本进程 ==="
show_status backend
show_status frontend
