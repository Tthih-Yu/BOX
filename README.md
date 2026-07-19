# 物料拉动系统 — 部署与运维说明

> 面向部署者与运维的操作手册。系统已在本机（Ubuntu）落地运行，本文档说明如何访问、维护、排障，以及机器换位置换 IP 后如何应对。
> 网络连通性的完整排查与结论见 `Ubuntu部署方案_整合版.md`。

## 1. 系统构成

| 组件 | 说明 | 端口 |
|---|---|---|
| Nginx | 对外唯一入口，提供前端页面 + 反代 `/api` 到后端 | 80 |
| 后端 Spring Boot | 业务后端，仅本机监听 | 127.0.0.1:8080 |
| MySQL | 数据库 | 127.0.0.1:3306 |
| 前端 Vue | 已构建为静态文件，由 Nginx 提供 | （`frontend/dist`） |
| 地址播报 | IP 变化时更新访问地址文件 | — |

访问链路：

```text
各网段用户浏览器
  → http://<本机IP>/          (80 端口)
  → Nginx
      ├─ /              → 前端静态页面 (frontend/dist)
      └─ /api/、/api/ws → 反代 127.0.0.1:8080 (后端)
```

前后端同源，无跨域（CORS）问题。

## 2. 如何访问

1. 打开家目录的 `物料拉动系统-访问地址.txt`，里面是当前访问地址（形如 `http://10.242.36.66`）。
2. 用浏览器访问该地址即可打开系统。
3. 管理员默认账号 `admin`，初始密码见 `deploy/scripts/material-pull.env` 中的 `MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD`。

> 同公司不同网段的设备均可直接访问，无需经 Windows Server 中转。

## 3. 开机自启

系统已配置为开机自动启动，重启机器后无需任何手动操作。启动顺序：

```text
开机 → mysql → 后端(8080) → nginx(80) → 地址播报
```

四个开机自启服务：

| 服务 | 作用 |
|---|---|
| `mysql` | 数据库 |
| `material-pull-backend-dev` | 后端 |
| `nginx` | 80 端口入口 |
| `material-pull-url-announcer` | 访问地址播报 |

查看是否都已启用：

```bash
systemctl is-enabled mysql nginx material-pull-backend-dev material-pull-url-announcer
```

## 4. 换位置 / 换 IP 怎么办

这台机器可能换位置插网线导致 IP 变化。本方案对此免疫：

- **服务端零改动**：Nginx `server_name _` 接受任意 IP，后端反代走本机 `127.0.0.1`，与外部 IP 无关。换 IP 后什么都不用改。
- **地址自动更新**：IP 变化后 15 秒内，家目录 `物料拉动系统-访问地址.txt` 会自动更新为新地址。打开它就知道把什么地址发给同事。

换位置后的唯一动作：打开 `物料拉动系统-访问地址.txt`，把里面的新地址发给使用者。

## 5. 日常运维命令

```bash
# 查看各服务状态
systemctl status nginx material-pull-backend-dev mysql

# 查看后端日志
tail -f /home/tthih/IE/logs/backend.log
# 或
sudo journalctl -u material-pull-backend-dev -f

# 重启后端（改了配置或更新 jar 后）
sudo systemctl restart material-pull-backend-dev

# 重启 Nginx（改了 nginx 配置后）
sudo nginx -t && sudo systemctl reload nginx

# 查看当前访问地址
cat ~/物料拉动系统-访问地址.txt
```

## 6. 更新部署

**更新前端**（改了前端代码后）：

```bash
cd /home/tthih/IE/frontend && npm run build
# dist 更新后 Nginx 立即生效，无需重启
```

**更新后端**（改了后端代码后）：

```bash
cd /home/tthih/IE/backend && mvn clean package -DskipTests
sudo systemctl restart material-pull-backend-dev
```

## 7. 首次部署 / 重装机器

在一台干净的 Ubuntu 上从零部署：

```bash
# 1. 构建前端
cd /home/tthih/IE/frontend && npm run build

# 2. 装后端开机自启（含 mysql profile；需 root）
sudo bash /home/tthih/IE/deploy/scripts/install-autostart.sh

# 3. 装 Nginx 80 入口（需 root）
sudo bash /home/tthih/IE/deploy/scripts/setup-nginx-gateway.sh

# 4. 装访问地址播报（需 root）
sudo cp /home/tthih/IE/deploy/systemd/material-pull-url-announcer.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now material-pull-url-announcer.service
```

数据库连接、初始管理员密码、CORS 等配置见 `deploy/scripts/material-pull.env`。

## 8. 常见问题

**打不开页面 / 白页**
- 确认后端在跑：`curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/api/health/ready`（应为 200）。
- 确认 Nginx 在跑：`systemctl status nginx`。
- 确认前端已构建：`ls /home/tthih/IE/frontend/dist/index.html`。

**登录报错 / 403**
- 本架构前后端同源，正常不会有 CORS 403。若出现，检查是否有其他残留后端进程占用 8080：`ss -ltnp | grep 8080` 应只有 systemd 管理的那个。

**别的网段打不开，但本机能开**
- 确认访问用的是 **80 端口**（非标准端口会被公司中间网络设备拦截，详见 `Ubuntu部署方案_整合版.md`）。

**换了 IP 后同事打不开**
- 让他们用 `物料拉动系统-访问地址.txt` 里的最新地址。

## 9. 相关文档

- `Ubuntu部署方案_整合版.md` — 网络连通性排查全过程与最终架构定案（含实测证据）。
- `项目文档.md` — 项目功能与业务说明。
- `deploy/scripts/material-pull.env` — 部署环境变量配置。

