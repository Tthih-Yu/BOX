#!/usr/bin/env bash
# 物料拉动系统 - Nginx 80 端口对外入口一键安装
# 用途：安装 nginx，部署 material-pull.conf，指向已构建的前端 dist 与本机后端 8080。
# 需 root：sudo bash deploy/scripts/setup-nginx-gateway.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONF_SRC="$PROJECT_ROOT/deploy/nginx/material-pull.conf"
CONF_AVAIL="/etc/nginx/sites-available/material-pull.conf"
CONF_ENABLED="/etc/nginx/sites-enabled/material-pull.conf"
DIST_DIR="$PROJECT_ROOT/frontend/dist"

if [[ "$EUID" -ne 0 ]]; then
  echo "请用 sudo 运行：sudo bash $0"
  exit 1
fi

if [[ ! -f "$CONF_SRC" ]]; then
  echo "未找到 nginx 配置: $CONF_SRC"
  exit 1
fi

if [[ ! -f "$DIST_DIR/index.html" ]]; then
  echo "未找到前端构建产物: $DIST_DIR/index.html"
  echo "请先在 frontend 目录执行: npm run build"
  exit 1
fi

# 清理可能占用 80 端口的临时测试服务（如 python -m http.server 80），否则 nginx 无法绑定
echo "== 清理占用 80 端口的临时进程 =="
pkill -f "http.server 80" 2>/dev/null || true
fuser -k 80/tcp 2>/dev/null || true
sleep 1

if ! command -v nginx >/dev/null 2>&1; then
  echo "== 安装 nginx =="
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y nginx
fi

echo "== 部署配置 =="
cp "$CONF_SRC" "$CONF_AVAIL"
ln -sfn "$CONF_AVAIL" "$CONF_ENABLED"
rm -f /etc/nginx/sites-enabled/default

# nginx worker 以 www-data 运行，需能读取 dist 目录（家目录默认 700，需放行执行位）
echo "== 放行静态目录读取权限 =="
chmod o+x /home/tthih /home/tthih/IE /home/tthih/IE/frontend 2>/dev/null || true
chmod -R o+rX "$DIST_DIR" 2>/dev/null || true

echo "== 测试并重载 nginx =="
nginx -t
systemctl enable nginx
systemctl restart nginx

echo
echo "完成。对外入口: http://<本机IP>/"
echo "本机自测: curl -s -o /dev/null -w '%{http_code}\\n' http://127.0.0.1/"
echo "后端需在 127.0.0.1:8080 运行（bash $SCRIPT_DIR/start.sh backend）"
