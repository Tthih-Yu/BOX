# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# 可选 Maven 镜像加速（内网 / 国内构建时传入），例如：
#   --build-arg MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ARG MAVEN_MIRROR_URL=""
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
      mkdir -p /root/.m2 && \
      printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; \
    fi

# 先单独拷贝 pom.xml 以利用依赖缓存层
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B clean package -DskipTests \
    && cp target/material-pull-system-backend-*.jar /workspace/app.jar

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# 时区设置为上海，与应用 Jackson 配置保持一致
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    MATERIAL_PULL_LOG_DIR="/app/logs"

# 安装 curl 供健康检查使用，并创建非 root 运行用户
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app \
    && mkdir -p /app/data /app/logs && chown -R app:app /app

COPY --from=build /workspace/app.jar /app/app.jar
RUN chown app:app /app/app.jar

USER app

# H2 文件库落在 /app/data，日志落在 /app/logs，挂载这两个目录即可持久化
VOLUME ["/app/data", "/app/logs"]

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/api/health/ready || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
