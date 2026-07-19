#!/usr/bin/env sh
# 离线镜像打包脚本：在联网机器上构建并导出镜像 tar，便于拷到无外网的工厂机器导入。
# 用法：
#   在联网机器： ./offline-images.sh save
#   在工厂机器： ./offline-images.sh load
set -eu

ACTION="${1:-save}"
APP_VERSION="${APP_VERSION:-0.8.4}"
OUT_DIR="${OUT_DIR:-./offline-images}"
BACKEND_IMAGE="material-pull-backend:${APP_VERSION}"
FRONTEND_IMAGE="material-pull-frontend:${APP_VERSION}"
# 集群 / MySQL / 备份所需的基础镜像
BASE_IMAGES="mysql:8.0 redis:7-alpine"

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

case "$ACTION" in
  save)
    mkdir -p "$OUT_DIR"
    echo "[offline] 构建业务镜像（如需镜像加速，先设置 MAVEN_MIRROR_URL / NPM_REGISTRY）"
    docker compose -f "$PROJECT_ROOT/docker-compose.yml" build
    echo "[offline] 拉取基础镜像 $BASE_IMAGES"
    for img in $BASE_IMAGES; do docker pull "$img"; done
    echo "[offline] 导出镜像到 $OUT_DIR/material-pull-images.tar"
    docker save -o "$OUT_DIR/material-pull-images.tar" \
      "$BACKEND_IMAGE" "$FRONTEND_IMAGE" $BASE_IMAGES
    echo "[offline] 完成。把整个项目目录 + $OUT_DIR 拷到工厂机器，执行： ./offline-images.sh load"
    ;;
  load)
    TAR="${2:-$OUT_DIR/material-pull-images.tar}"
    echo "[offline] 从 $TAR 导入镜像"
    docker load -i "$TAR"
    echo "[offline] 完成。现在可直接 docker compose up -d（无需重新 build）"
    ;;
  *)
    echo "用法： $0 save | load [tar路径]" >&2
    exit 1
    ;;
esac
