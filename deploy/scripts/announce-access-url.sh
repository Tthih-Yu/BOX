#!/usr/bin/env bash
# 物料拉动系统 - 访问地址播报
# 监测本机主用 IP，变化时把当前访问地址写到显眼位置：
#   - 桌面文件 ~/物料拉动系统-访问地址.txt
#   - 日志 logs/access-url.log
# 换位置插网线导致 IP 变化后，打开桌面那个文件即可看到最新地址。
set -uo pipefail

RUN_USER="${SUDO_USER:-${USER:-tthih}}"
HOME_DIR="$(getent passwd "$RUN_USER" | cut -d: -f6)"
HOME_DIR="${HOME_DIR:-/home/$RUN_USER}"

PROJECT_ROOT="/opt/apps/material-pull"
LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR" 2>/dev/null || true

DESKTOP_FILE="$HOME_DIR/物料拉动系统-访问地址.txt"
LOG_FILE="$LOG_DIR/access-url.log"
PORT=80

primary_ip() {
  # 取默认路由所用的源 IP（即对外主用地址），排除回环/docker
  ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K[0-9.]+' | head -1
}

all_lan_ips() {
  ip -4 -brief addr show 2>/dev/null \
    | grep -vE '^(lo|docker|br-|veth)' \
    | grep -oP '\d+\.\d+\.\d+\.\d+' | sort -u
}

write_report() {
  local ip="$1"
  local ts; ts="$(date '+%Y-%m-%d %H:%M:%S')"
  local url="http://$ip"
  [[ "$PORT" != "80" ]] && url="http://$ip:$PORT"

  {
    echo "物料拉动系统 - 当前访问地址"
    echo "更新时间：$ts"
    echo
    echo "    $url"
    echo
    echo "把上面的地址发给需要访问的同事即可。"
    echo "（换位置插网线后地址可能变化，本文件会自动更新。）"
    echo
    echo "本机所有可用地址："
    local i
    for i in $(all_lan_ips); do
      local u="http://$i"; [[ "$PORT" != "80" ]] && u="http://$i:$PORT"
      echo "  - $u"
    done
  } > "$DESKTOP_FILE" 2>/dev/null

  chown "$RUN_USER":"$RUN_USER" "$DESKTOP_FILE" 2>/dev/null || true
  echo "[$ts] 访问地址变更为 $url" >> "$LOG_FILE"
}

LAST=""
while true; do
  IP="$(primary_ip)"
  if [[ -n "$IP" && "$IP" != "$LAST" ]]; then
    write_report "$IP"
    LAST="$IP"
  fi
  sleep 15
done
