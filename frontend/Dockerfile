# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /app

# 可选 npm 镜像加速（内网 / 国内构建时传入），例如：
#   --build-arg NPM_REGISTRY=https://registry.npmmirror.com
ARG NPM_REGISTRY=""
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi

# 利用缓存：先装依赖再拷源码
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_APP_TITLE
ARG VITE_APP_VERSION
ENV VITE_APP_TITLE=${VITE_APP_TITLE} \
    VITE_APP_VERSION=${VITE_APP_VERSION}
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine AS runtime

# 自定义 nginx 配置：静态资源 + /api 反向代理到后端容器
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1
