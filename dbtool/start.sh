#!/usr/bin/env bash
# 数据库可视化工具启动脚本
# 用法: bash start.sh [start|stop|restart|status]
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$DIR/dbtool.pid"
LOG_FILE="$DIR/dbtool.log"

is_running() { [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; }

start() {
  if is_running; then echo "[dbtool] 已在运行 (PID $(cat "$PID_FILE"))"; exit 0; fi
  cd "$DIR"
  nohup python3 "$DIR/server.py" >>"$LOG_FILE" 2>&1 &
  echo $! >"$PID_FILE"
  sleep 1
  if is_running; then
    echo "[dbtool] 已启动 PID=$(cat "$PID_FILE")，日志: $LOG_FILE"
    grep -m1 "启动：" "$LOG_FILE" 2>/dev/null || true
  else
    echo "[dbtool] 启动失败，见 $LOG_FILE"; tail -5 "$LOG_FILE" 2>/dev/null; exit 1
  fi
}

stop() {
  if is_running; then
    kill "$(cat "$PID_FILE")" 2>/dev/null || true
    rm -f "$PID_FILE"
    echo "[dbtool] 已停止"
  else
    echo "[dbtool] 未在运行"
  fi
  rm -f "$PID_FILE"
}

case "${1:-start}" in
  start) start ;;
  stop) stop ;;
  restart) stop; sleep 1; start ;;
  status) is_running && echo "[dbtool] 运行中 PID=$(cat "$PID_FILE")" || echo "[dbtool] 未运行" ;;
  *) echo "用法: $0 [start|stop|restart|status]"; exit 1 ;;
esac
