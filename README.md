# 物料拉动系统

面向工厂现场的物料拉动与补货配送系统。通过标签扫码触发补货，串联仓库任务、库存、配送、收货和空盒回收，形成可追溯的闭环。

## 核心能力

- 标签、物料、工位与扫码规则管理
- 扫码拉动、补货任务、拣料配送与收货确认
- 库存、周转盒、A/B 双盒、计划与看板管理
- 仓库标签打印及 Windows 打印代理
- AGV、SAP/IMS 等外部系统接口
- 用户权限、数据范围、操作日志与健康检查

## 架构

```text
Web 管理端 / 安卓扫码端
          │
        Nginx
          │
Vue 3 前端 ── Spring Boot 后端 ── MySQL / Redis
                         │
                 标签打印代理、AGV 与外部接口
```

## 项目结构

- `backend/`：Java 17 + Spring Boot 业务后端
- `frontend/`：Vue 3 + TypeScript + Vite 管理端
- `print-agent/`：Windows 斑马标签打印代理
- `androidAPP/`：安卓扫码端
- `deploy/`：Nginx、systemd 与部署脚本
- `docs/`：运行、导入、SQL 与协作说明

## 快速启动（开发）

需要 Java 17、Node.js 和 npm。后端开发环境可使用 H2，生产环境使用 MySQL。

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
cd frontend
npm install
npm run dev
```

前端默认访问 `http://localhost:5173`，后端 API 默认运行在 `http://localhost:8080/api`。

## 说明

独立一人完成全周期开发、测试、部署、运维。
并该系统正在不断完善/增加新功能
