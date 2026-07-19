#!/usr/bin/env bash
# 物料拉动系统 - Ubuntu 24.04 一键部署脚本
# 作用：在全新 Ubuntu 24.04 机器上安装 Docker Engine + Compose 插件，
#       准备 .env，构建前后端镜像并启动整套系统。
#
# 用法（在项目根目录 IE/ 下执行）：
#   sudo bash deploy/scripts/ubuntu-setup.sh              # H2 文件库，开箱即用
#   sudo bash deploy/scripts/ubuntu-setup.sh --mysql      # 使用 MySQL
#   sudo bash deploy/scripts/ubuntu-setup.sh --offline     # 离线导入镜像后启动(不联网构建)
set -euo pipefail

# ---------- 解析参数 ----------
USE_MYSQL=0
OFFLINE=0
for arg in "$@"; do
  case "$arg" in
    --mysql) USE_MYSQL=1 ;;
    --offline) OFFLINE=1 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "未知参数：$arg（可用 --mysql / --offline）"; exit 1 ;;
  esac
done

# ---------- 定位项目根目录 ----------
SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

log() { echo -e "\033[1;32m[setup]\033[0m $*"; }
warn() { echo -e "\033[1;33m[setup]\033[0m $*"; }
die() { echo -e "\033[1;31m[setup]\033[0m $*" >&2; exit 1; }

# ---------- 必须 root ----------
if [ "$(id -u)" -ne 0 ]; then
  die "请用 sudo 运行：sudo bash deploy/scripts/ubuntu-setup.sh"
fi

# 真实登录用户（sudo 场景下把该用户加入 docker 组）
REAL_USER="${SUDO_USER:-$(logname 2>/dev/null || echo root)}"

# ---------- 安装 Docker Engine + Compose 插件 ----------
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  log "已检测到 Docker 与 compose 插件，跳过安装。"
else
  log "安装 Docker Engine 与 Compose 插件（官方源，适配 Ubuntu 24.04 noble）..."
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y ca-certificates curl gnupg
  install -m 0755 -d /etc/apt/keyrings
  if [ ! -f /etc/apt/keyrings/docker.gpg ]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
      | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
  fi
  ARCH="$(dpkg --print-architecture)"
  CODENAME="$(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")"
  echo "deb [arch=$ARCH signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $CODENAME stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
  log "Docker 安装完成：$(docker --version)"
fi

# 把当前登录用户加入 docker 组，之后可免 sudo 使用（需重新登录生效）
if [ "$REAL_USER" != "root" ]; then
  usermod -aG docker "$REAL_USER" 2>/dev/null || true
  log "已把用户 $REAL_USER 加入 docker 组（重新登录后免 sudo 使用 docker）。"
fi

# ---------- 准备 .env ----------
if [ ! -f "$PROJECT_ROOT/.env" ]; then
  if [ -f "$PROJECT_ROOT/.env.example" ]; then
    cp "$PROJECT_ROOT/.env.example" "$PROJECT_ROOT/.env"
    log "已从 .env.example 生成 .env。"
  else
    die "缺少 .env.example，无法生成 .env。"
  fi

  # 自动生成强随机设备/外部密钥，替换掉模板里的占位值（生产要求 ≥16 位强随机）
  RANDOM_KEY="$(openssl rand -base64 24 2>/dev/null | tr -d '/+=' | cut -c1-32 || echo "")"
  if [ -n "$RANDOM_KEY" ]; then
    sed -i "s#^MATERIAL_PULL_EXTERNAL_API_KEY=.*#MATERIAL_PULL_EXTERNAL_API_KEY=$RANDOM_KEY#" "$PROJECT_ROOT/.env"
    log "已为 MATERIAL_PULL_EXTERNAL_API_KEY 生成强随机值。"
  fi
  # .env 含敏感信息，收紧权限
  chmod 600 "$PROJECT_ROOT/.env" || true
  chown "$REAL_USER":"$REAL_USER" "$PROJECT_ROOT/.env" 2>/dev/null || true
  warn "请检查 .env：管理员密码、CORS 白名单(MATERIAL_PULL_CORS_ALLOWED_ORIGINS)、以及(用 MySQL 时)数据库密码。"
else
  log "检测到已有 .env，保留不覆盖。"
fi

# ---------- 启动 ----------
COMPOSE_ARGS=""
if [ "$USE_MYSQL" -eq 1 ]; then
  log "启用 MySQL 模式。"
  # 首发到空库：临时用 update 让 Hibernate 建表；稳态后可改回 validate（见 DOCKER.md 第四节）
  export SPRING_PROFILES_ACTIVE="mysql"
  export MATERIAL_PULL_MYSQL_DDL_AUTO="${MATERIAL_PULL_MYSQL_DDL_AUTO:-update}"
  COMPOSE_ARGS="--profile mysql"
fi

if [ "$OFFLINE" -eq 1 ]; then
  log "离线模式：从 offline-images 导入镜像（不联网构建）..."
  sh "$SCRIPT_DIR/offline-images.sh" load
  # shellcheck disable=SC2086
  docker compose $COMPOSE_ARGS up -d
else
  log "构建并启动前后端（首次构建拉取依赖，耗时数分钟）..."
  # shellcheck disable=SC2086
  docker compose $COMPOSE_ARGS up -d --build
fi

log "等待服务就绪..."
sleep 5
docker compose $COMPOSE_ARGS ps || true

HTTP_PORT_VAL="$(grep -E '^HTTP_PORT=' "$PROJECT_ROOT/.env" | cut -d= -f2 || echo 80)"
HTTP_PORT_VAL="${HTTP_PORT_VAL:-80}"
IP_ADDR="$(hostname -I 2>/dev/null | awk '{print $1}')"

echo
log "部署完成。"
echo "  访问地址： http://${IP_ADDR:-<本机IP>}${HTTP_PORT_VAL:+:$HTTP_PORT_VAL}"
echo "  管理员账号：admin（密码见 .env 的 MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD）"
echo
echo "  查看状态： docker compose ps"
echo "  跟踪日志： docker compose logs -f backend"
echo "  停止服务： docker compose down"
if [ "$USE_MYSQL" -eq 1 ]; then
  echo
  warn "MySQL 首发已用 ddl-auto=update 自动建表。确认无误后，按 DOCKER.md 第四节改回 validate 稳态运行。"
fi
