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

stop_one() {
  local name="$1"
  local pid_file="$PID_DIR/$name.pid"

  if [[ ! -f "$pid_file" ]]; then
    echo "[$name] 未运行（无 PID 文件）"
    return 0
  fi

  local pid
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null; then
    echo "[$name] 停止 PID=$pid"
    kill "$pid" 2>/dev/null || true
    for _ in {1..20}; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.5
    done
    if kill -0 "$pid" 2>/dev/null; then
      echo "[$name] 强制结束"
      kill -9 "$pid" 2>/dev/null || true
    fi
  else
    echo "[$name] 进程不存在"
  fi

  rm -f "$pid_file"
}

case "${1:-all}" in
  backend) stop_one backend ;;
  frontend) stop_one frontend ;;
  all)
    stop_one frontend
    stop_one backend
    ;;
  *)
    echo "用法: $0 [all|backend|frontend]"
    exit 1
    ;;
esac
