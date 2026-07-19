#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$SCRIPT_DIR/material-pull.env"
ENV_EXAMPLE="$SCRIPT_DIR/material-pull.env.example"
RUN_USER="$(whoami)"

if [[ "$EUID" -ne 0 ]]; then
  echo "请使用 sudo 运行此脚本以安装 systemd 开机自启："
  echo "  sudo bash $0"
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  sed -i "s|^RUN_USER=.*|RUN_USER=$RUN_USER|" "$ENV_FILE"
  sed -i "s|^PROJECT_ROOT=.*|PROJECT_ROOT=$PROJECT_ROOT|" "$ENV_FILE"
  echo "已创建配置文件: $ENV_FILE"
  echo "如需修改密码或端口模式，请先编辑该文件。"
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
LOG_DIR="${LOG_DIR:-$PROJECT_ROOT/logs}"
BACKEND_JAR="${BACKEND_JAR:-material-pull-system-backend-0.8.4.jar}"
SPRING_PROFILE="${SPRING_PROFILE:-dev}"
FRONTEND_MODE="${FRONTEND_MODE:-dev:lan}"
ACTUAL_USER="${SUDO_USER:-$RUN_USER}"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "未找到 Java 17: $JAVA_HOME"
  echo "请安装 openjdk-17-jdk 或修改 material-pull.env 中的 JAVA_HOME"
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "未找到 npm，请先安装 Node.js 20"
  exit 1
fi

mkdir -p "$LOG_DIR" "$PROJECT_ROOT/.run"
chown -R "$ACTUAL_USER:$ACTUAL_USER" "$LOG_DIR" "$PROJECT_ROOT/.run"

render_service() {
  local template="$1"
  local output="$2"
  sed \
    -e "s|@PROJECT_ROOT@|$PROJECT_ROOT|g" \
    -e "s|@RUN_USER@|$ACTUAL_USER|g" \
    -e "s|@JAVA_HOME@|$JAVA_HOME|g" \
    -e "s|@LOG_DIR@|$LOG_DIR|g" \
    -e "s|@BACKEND_JAR@|$BACKEND_JAR|g" \
    -e "s|@SPRING_PROFILE@|$SPRING_PROFILE|g" \
    -e "s|@FRONTEND_MODE@|$FRONTEND_MODE|g" \
    "$template" >"$output"
}

TMP_BACKEND="$(mktemp)"
TMP_FRONTEND="$(mktemp)"
render_service "$PROJECT_ROOT/deploy/systemd/material-pull-backend-dev.service" "$TMP_BACKEND"
render_service "$PROJECT_ROOT/deploy/systemd/material-pull-frontend-dev.service" "$TMP_FRONTEND"

cp "$TMP_BACKEND" /etc/systemd/system/material-pull-backend-dev.service
cp "$TMP_FRONTEND" /etc/systemd/system/material-pull-frontend-dev.service
rm -f "$TMP_BACKEND" "$TMP_FRONTEND"

echo "打包后端 jar（如尚未构建）..."
sudo -u "$ACTUAL_USER" env JAVA_HOME="$JAVA_HOME" PATH="$JAVA_HOME/bin:$PATH" \
  bash -c "test -f '$PROJECT_ROOT/backend/target/$BACKEND_JAR' || mvn -f '$PROJECT_ROOT/backend/pom.xml' clean package -DskipTests -q"

echo "安装前端依赖（如尚未安装）..."
sudo -u "$ACTUAL_USER" bash -c "test -d '$PROJECT_ROOT/frontend/node_modules' || npm --prefix '$PROJECT_ROOT/frontend' install"

systemctl daemon-reload
systemctl enable material-pull-backend-dev.service material-pull-frontend-dev.service
systemctl restart material-pull-backend-dev.service material-pull-frontend-dev.service

echo
echo "开机自启已安装并启动。"
echo
systemctl --no-pager status material-pull-backend-dev.service | sed -n '1,8p'
echo
systemctl --no-pager status material-pull-frontend-dev.service | sed -n '1,8p'
echo
echo "常用命令："
echo "  查看状态: sudo systemctl status material-pull-backend-dev material-pull-frontend-dev"
echo "  查看日志: sudo journalctl -u material-pull-backend-dev -f"
echo "            sudo journalctl -u material-pull-frontend-dev -f"
echo "            tail -f $LOG_DIR/backend.log"
echo "            tail -f $LOG_DIR/frontend.log"
echo "  停止自启: sudo systemctl disable --now material-pull-backend-dev material-pull-frontend-dev"
echo "  手动启停: bash $SCRIPT_DIR/start.sh | bash $SCRIPT_DIR/stop.sh"
