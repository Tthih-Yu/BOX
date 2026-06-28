#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${MATERIAL_PULL_ENV:-$SCRIPT_DIR/material-pull.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少配置文件: $ENV_FILE"
  echo "请先执行: cp $SCRIPT_DIR/material-pull.env.example $ENV_FILE"
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
LOG_DIR="${LOG_DIR:-$PROJECT_ROOT/logs}"
PID_DIR="${PID_DIR:-$PROJECT_ROOT/.run}"
BACKEND_JAR="${BACKEND_JAR:-material-pull-system-backend-0.8.4.jar}"
SPRING_PROFILE="${SPRING_PROFILE:-dev}"
FRONTEND_MODE="${FRONTEND_MODE:-dev:lan}"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p "$LOG_DIR" "$PID_DIR"

is_running() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null
}

start_backend() {
  local pid_file="$PID_DIR/backend.pid"
  if is_running "$pid_file"; then
    echo "[backend] 已在运行 (PID $(cat "$pid_file"))"
    return 0
  fi

  local jar_path="$PROJECT_ROOT/backend/target/$BACKEND_JAR"
  local mvn_repo="${MAVEN_LOCAL_REPO:-$HOME/.m2/repository}"
  if [[ "$(id -u)" -eq 0 && ! -w "$(dirname "$mvn_repo")" ]]; then
    # root 执行时默认的 ~/.m2 可能没法创建，回退到项目内置缓存
    if [[ -d "$PROJECT_ROOT/.m2-repo" ]]; then
      mvn_repo="$PROJECT_ROOT/.m2-repo"
    elif [[ -d "/home/wan/.m2/repository" ]]; then
      mvn_repo="/home/wan/.m2/repository"
    fi
  fi
  echo "[backend] 正在打包最新后端 jar... (maven repo=$mvn_repo)"
  if ! (cd "$PROJECT_ROOT/backend" && mvn -o clean package -DskipTests -q -Dmaven.repo.local="$mvn_repo"); then
    echo "[backend] mvn 打包失败"
    if [[ -f "$jar_path" ]]; then
      echo "[backend] 复用现有 jar：$jar_path"
    else
      echo "[backend] 没有可用的 jar，启动终止"
      exit 1
    fi
  fi

  echo "[backend] 启动中 (profile=$SPRING_PROFILE)..."
  (
    cd "$PROJECT_ROOT/backend"
    if [[ -n "${MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD:-}" ]]; then
      export MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD
    fi
    nohup java -jar "$jar_path" --spring.profiles.active="$SPRING_PROFILE" \
      >>"$LOG_DIR/backend.log" 2>&1 &
    echo $! >"$pid_file"
  )

  # Spring Boot 启动到能监听 8080 通常需要 6-10 秒，加长检测窗口
  local ok=0
  for _ in {1..30}; do
    sleep 1
    if is_running "$pid_file" && grep -q "Started MaterialPullApplication" "$LOG_DIR/backend.log" 2>/dev/null; then
      ok=1
      break
    fi
  done
  if [[ $ok -eq 1 ]]; then
    echo "[backend] 已启动 PID=$(cat "$pid_file")，日志: $LOG_DIR/backend.log"
    echo "[backend] API: http://localhost:8080/api"
  else
    echo "[backend] 启动失败或超时，请查看: $LOG_DIR/backend.log"
    exit 1
  fi
}

start_frontend() {
  local pid_file="$PID_DIR/frontend.pid"
  if is_running "$pid_file"; then
    echo "[frontend] 已在运行 (PID $(cat "$pid_file"))"
    return 0
  fi

  if [[ ! -d "$PROJECT_ROOT/frontend/node_modules" ]]; then
    echo "[frontend] 安装依赖..."
    (cd "$PROJECT_ROOT/frontend" && npm install)
  fi

  echo "[frontend] 启动中 (npm run $FRONTEND_MODE)..."
  (
    cd "$PROJECT_ROOT/frontend"
    nohup npm run "$FRONTEND_MODE" >>"$LOG_DIR/frontend.log" 2>&1 &
    echo $! >"$pid_file"
  )

  sleep 2
  if is_running "$pid_file"; then
    echo "[frontend] 已启动 PID=$(cat "$pid_file")，日志: $LOG_DIR/frontend.log"
    if [[ "$FRONTEND_MODE" == "dev:lan" ]]; then
      echo "[frontend] 访问: http://$(hostname -I | awk '{print $1}'):5173"
    else
      echo "[frontend] 访问: http://localhost:5173"
    fi
  else
    echo "[frontend] 启动失败，请查看: $LOG_DIR/frontend.log"
    exit 1
  fi
}

case "${1:-all}" in
  backend) start_backend ;;
  frontend) start_frontend ;;
  all)
    start_backend
    start_frontend
    ;;
  *)
    echo "用法: $0 [all|backend|frontend]"
    exit 1
    ;;
esac
