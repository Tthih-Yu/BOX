# Aliyun API Relay

更新时间：2026-08-21

> 当前状态：阿里云服务器已重置，基础软件和 `tthih.top` 证书已恢复，但 API 已部署到 `tthih.top`，公网HTTPS与真实指令闭环均通过；WinServer 仍运行旧版 EXE。下文“已经完成”的联调结果均为重置前历史结果。

公司 Linux 经 WinServer 应用层中继，与阿里云进行标准 HTTP/HTTPS 业务通信。

本项目用于上传业务数据、轮询阿里云任务并回传处理结果。它不是通用代理，不提供 SSH、WebSocket、任意 TCP 或任意 URL 转发能力。

## 1. 背景与结论

当前网络测试已经确认：

- 公司 Linux 直接访问 `tunnel.tthih.top` 时会被 Aptiv HTTPS 透明代理处理，并命中 `rule 96`。
- WebSocket 请求被返回 HTTP 302。
- HTTP/2 隧道请求在阿里云端表现为 `GOAWAY / PROTOCOL_ERROR`。
- WinServer 到企业微信的标准 HTTPS POST 可以正常工作。
- Linux 到 WinServer 的普通内网 HTTP/TCP 可以正常传输。

因此采用与现有企业微信中转服务相同的模式：Linux 只访问内网 WinServer，由 WinServer 使用自己的公网出口向阿里云发送普通 HTTPS/1.1 请求。

## 2. 总体架构

```text
公司 Linux 10.243.129.131
  │ 内网 HTTP + X-Relay-Token
  ▼
WinServer Relay 10.243.111.152:80
  │ 标准 HTTPS/1.1 + Bearer ApiToken
  ▼
tthih.top:443
  │ Nginx
  ▼
阿里云 API 127.0.0.1:18082 + SQLite
```

所有跨边界连接均由内向外主动发起。阿里云需要向 Linux 下发任务时，由 Linux 定时轮询，不建立公网入站隧道。

## 3. 设计目标

- 使用标准 HTTP/1.1 GET/POST，避免 SSH、WebSocket 和 HTTP/2 隧道特征。
- WinServer 只向配置中指定的上游 HTTPS 地址和固定 API 路径转发，不能成为开放代理。
- Linux 与 WinServer、WinServer 与阿里云使用不同密钥。
- 支持数据上传、任务下发、结果回传和健康检查。
- 限制来源地址、方法、路径、请求大小、并发和超时。
- 阿里云 API 仅监听回环地址，通过 Nginx 提供公网 HTTPS。
- 使用 SQLite 保存数据和任务，服务重启后记录不丢失。

## 4. 项目目录

```text
external-scan-relay/
├── README.md
├── cloud-api/
│   ├── server.py
│   ├── api.env.example
│   ├── aliyun-api-relay.service
│   └── nginx-api.conf
├── win-relay/
│   ├── AliyunApiRelay.csproj
│   ├── Program.cs
│   ├── RelayConfig.cs
│   ├── RelayHost.cs
│   ├── relay-config.example.json
│   └── install-service.ps1
└── linux-client/
    ├── relay-client.sh
    ├── relay.env.example
    └── payload.example.json
```

- `cloud-api`：Python 标准库 API、SQLite、systemd 和 Nginx 示例。
- `win-relay`：运行在 WinServer 的 .NET Framework 4.7.2 Windows 服务/图形界面。
- `linux-client`：Linux 端基于 curl 的调用脚本。

## 5. 固定接口映射

| Linux 调用 WinServer | WinServer 固定转发 | 用途 |
|---|---|---|
| `GET /aliyun-relay/health` | `GET /health` | 检查完整链路 |
| `POST /aliyun-relay/data` | `POST /api/data` | 上传业务数据 |
| `GET /aliyun-relay/tasks` | `GET /api/tasks` | 轮询待处理任务 |
| `POST /aliyun-relay/tasks/{id}/result` | `POST /api/tasks/{id}/result` | 回传任务结果 |

阿里云还提供 `POST /api/tasks`，仅供阿里云本机或受控管理端创建任务。WinServer 不向 Linux 暴露该接口，Linux 也无法改变上游域名、端口或路径。

### 5.1 通信流程

```text
健康检查：Ubuntu → WinServer /aliyun-relay/health → 阿里云 /health
上传数据：Ubuntu → WinServer /aliyun-relay/data → 阿里云 /api/data → SQLite
轮询任务：Ubuntu → WinServer /aliyun-relay/tasks → 阿里云 /api/tasks → SQLite
回传结果：Ubuntu → WinServer /aliyun-relay/tasks/{id}/result → 阿里云 /api/tasks/{id}/result → SQLite
```

## 6. 请求与响应示例

### 6.1 健康检查

```http
GET /aliyun-relay/health HTTP/1.1
Host: 10.243.111.152
X-Relay-Token: <RelayToken>
```

```json
{
  "success": true,
  "status": "UP",
  "time": "2026-08-20T08:33:36.698505+00:00"
}
```

### 6.2 上传数据

```http
POST /aliyun-relay/data HTTP/1.1
Content-Type: application/json
X-Relay-Token: <RelayToken>
```

```json
{
  "source": "material-pull",
  "type": "status",
  "time": "2026-08-20T16:30:00+08:00",
  "data": {
    "message": "example payload"
  }
}
```

接受后返回 HTTP 202：

```json
{
  "success": true,
  "id": "48bd378f-7a7d-4d43-a13e-8f325d0ca477"
}
```

### 6.3 轮询任务

```http
GET /aliyun-relay/tasks HTTP/1.1
X-Relay-Token: <RelayToken>
```

```json
{
  "success": true,
  "tasks": [
    {
      "id": "a93a2b73-3261-42c4-b0a0-406cdc670ebd",
      "createdAt": "2026-08-20T08:33:36.711469+00:00",
      "updatedAt": "2026-08-20T08:33:36.711469+00:00",
      "payload": {"type": "echo", "value": "hello"}
    }
  ]
}
```

### 6.4 回传结果

```http
POST /aliyun-relay/tasks/a93a2b73-3261-42c4-b0a0-406cdc670ebd/result HTTP/1.1
Content-Type: application/json
X-Relay-Token: <RelayToken>
```

```json
{"success": true, "output": "hello"}
```

提交成功后任务状态变为 `completed`，不会再次出现在待处理列表。

## 7. 鉴权与配置

系统使用两个完全独立的 Token：

| Token | 使用方向 | HTTP 头 |
|---|---|---|
| `RelayToken` | Linux → WinServer | `X-Relay-Token` |
| `ApiToken` | WinServer → 阿里云 | `Authorization: Bearer <ApiToken>` |

两个 Token 必须不同，长度至少 32 个字符：

```bash
openssl rand -hex 32
openssl rand -hex 32
```

真实 Token 不得写入源码、Git、README、聊天或截图。

### 7.1 WinServer 配置

模板：`win-relay/relay-config.example.json`

```json
{
  "ListenPrefix": "http://+:80/aliyun-relay/",
  "AllowedSourceIp": "10.243.129.131",
  "RelayToken": "<Linux到WinServer的Token>",
  "ApiBaseUrl": "https://tthih.top",
  "ApiToken": "<WinServer到阿里云的Token>",
  "MaxBodyBytes": 65536,
  "TimeoutSeconds": 20
}
```

上游目标、监听地址和 Token 均可在图形界面配置；两个 Token 至少 32 字符，请求体默认 64 KiB，超时默认 20 秒。

### 7.2 阿里云配置

模板：`cloud-api/api.env.example`

```dotenv
RELAY_API_HOST=127.0.0.1
RELAY_API_PORT=18082
RELAY_API_TOKEN=<与WinServer ApiToken相同>
RELAY_API_DB=/var/lib/aliyun-api-relay/relay.db
RELAY_API_MAX_BODY=65536
```

### 7.3 Linux 配置

模板：`linux-client/relay.env.example`

```dotenv
RELAY_URL=http://10.243.111.152/aliyun-relay
RELAY_TOKEN=<与WinServer RelayToken相同>
```

## 8. 部署顺序

### 8.1 前置条件

1. 公司批准 WinServer 访问 `tthih.top:443` 标准 HTTPS。
2. DNS 创建 `tthih.top` 并指向阿里云公网 IP。
3. 确认阿里云 443 端口方案。

当前环境已停用原 wstunnel，443 由 Nginx 接管。变更前必须保留 RDP/SSH 回退入口。

### 8.2 阿里云 API

先在 Windows 项目根目录上传文件：

```powershell
scp -r aliyun-api root@47.103.94.109:/home/admin/aliyun-api
```

然后在阿里云服务器执行：

```bash
sudo apt update
sudo apt install -y python3 nginx certbot python3-certbot-nginx

sudo useradd --system --home /var/lib/aliyun-api-relay --shell /usr/sbin/nologin aliyun-api-relay
sudo install -d -o aliyun-api-relay -g aliyun-api-relay -m 750 /var/lib/aliyun-api-relay

sudo install -d -m 755 /opt/aliyun-api-relay
sudo install -m 755 /home/admin/cloud-api/server.py /opt/aliyun-api-relay/server.py

sudo install -d -o root -g aliyun-api-relay -m 750 /etc/aliyun-api-relay
sudo install -o root -g aliyun-api-relay -m 640 /home/admin/cloud-api/api.env.example /etc/aliyun-api-relay/api.env
```

填写 `RELAY_API_TOKEN` 后：

```bash
sudo install -m 644 /home/admin/cloud-api/aliyun-api-relay.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now aliyun-api-relay
curl http://127.0.0.1:18082/health
```

停用原 wstunnel、申请证书并安装 Nginx 配置：

```bash
sudo systemctl stop wstunnel-server
sudo systemctl disable wstunnel-server
sudo certbot certonly --nginx -d tthih.top
sudo cp /home/admin/cloud-api/nginx-api.conf /etc/nginx/sites-available/tthih.top
sudo ln -s /etc/nginx/sites-available/tthih.top /etc/nginx/sites-enabled/tthih.top
sudo nginx -t
sudo systemctl reload nginx
curl https://tthih.top/health
```

### 8.2.1 阿里云部署常见问题

- `Unit aliyun-api-relay.service could not be found`：服务文件未安装。
- `status=226/NAMESPACE`：通常是 `/var/lib/aliyun-api-relay` 目录不存在或权限不对，或 `server.py` 未安装。
- `curl: (7) Failed to connect`：服务未启动或端口未监听；等待 1 秒后重试，并查看 `ss -ltnp | grep 18082` 和 `journalctl`。
- Nginx 报找不到 `api.tthih.top` 证书：执行 `sudo sed -i 's/api\.tthih\.top/tthih.top/g' /etc/nginx/sites-available/tthih.top`，再 `sudo nginx -t` 和 `sudo systemctl reload nginx`。
- wstunnel 占用 443：服务名是 `wstunnel-server.service`，用 `sudo systemctl stop wstunnel-server` 和 `sudo systemctl disable wstunnel-server` 停用。

### 8.3 WinServer Relay

1. 使用 Visual Studio 打开 `win-relay/AliyunApiRelay.csproj`，或运行 `dotnet build win-relay/AliyunApiRelay.csproj -c Release`。
2. 将输出 `win-relay/bin/Release/net472/AliyunApiRelay.exe`、安装脚本和配置复制到 WinServer。
5. 把配置模板改名为 `relay-config.json`（Token 也可以在图形界面里填写）。
6. 双击 `AliyunApiRelay.exe` 打开图形界面，在 UI 中填写参数并点击“保存配置”“启动”。
   界面中可填写公司 Ubuntu 来源 IP、阿里云 API 地址（IP 或域名）、两段 Token、请求大小和超时；点击“测试两端连接”可分别检查本端中继和阿里云连通性。

> 监听 `http://+:80/aliyun-relay/` 需要管理员权限；首次测试请以管理员身份运行，或先执行 `install-service.ps1` 添加 URL ACL。

另开管理员 PowerShell：

```powershell
Invoke-WebRequest http://127.0.0.1/aliyun-relay/health `
  -Headers @{ "X-Relay-Token" = "Linux到WinServer的Token" } `
  -UseBasicParsing
```

测试成功后，在图形界面点击“停止”，再安装服务：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\install-service.ps1
```

Windows 防火墙只允许 `10.243.129.131` 访问 TCP 80，不向公网开放。

### 8.4 Linux 客户端

```bash
sudo install -d -m 750 /etc/aliyun-api-relay
sudo install -m 600 linux-client/relay.env.example /etc/aliyun-api-relay/client.env
chmod +x linux-client/relay-client.sh
```

填写 `RelayToken` 后：

```bash
set -a
source /etc/aliyun-api-relay/client.env
set +a

./linux-client/relay-client.sh health
./linux-client/relay-client.sh send linux-client/payload.example.json
./linux-client/relay-client.sh tasks
```

回传结果：

```bash
printf '{"success":true,"output":"hello"}\n' > result.json
./linux-client/relay-client.sh result TASK_ID result.json
```

## 9. 安全边界

当前实现包含：

- WinServer 只允许 `10.243.129.131` 和回环地址访问。
- WinServer 只接受固定方法与路径，上游域名由配置指定。
- 两段链路使用不同 Token。
- 默认请求体限制 64 KiB，并发限制 8。
- 上游响应体限制 8 MiB，避免无界读入内存。
- 缺 Content-Length 返回 411，超大请求返回 413，非法分页参数返回 400。
- Linux curl 客户端在旧版 curl 上自动回退 `--fail`。
- 阿里云 API 只监听 `127.0.0.1:18082`。
- Nginx负责公网 TLS 终止。
- SQLite持久化数据和任务。
- 响应携带 `X-Request-Id`，便于跨端排查。

上线前还必须：

- 配置文件仅允许服务账户和管理员读取。
- 建立 Token 轮换流程。
- 确认公司 TLS 透明解密和数据合规要求。
- 为 Nginx 增加限流、日志轮换和请求大小限制。
- 严格校验业务 JSON 字段。
- 根据敏感度增加请求签名、防重放和业务数据加密。
- 将上游异常详情替换为内部错误码。
- 确定 SQLite 数据保留、归档和备份周期。

## 10. 当前状态

已经完成：

- Python API、SQLite 初始化和持久化（写事务已显式提交）。
- 数据上传、任务创建、轮询和结果回传。
- Bearer Token 鉴权。
- systemd 与 Nginx 示例。
- WinServer .NET Framework 4.7.2 中转源码和服务安装脚本。
- WinForms 图形配置界面，双击即可打开、填写参数并启动/停止。
- Linux curl 客户端。
- 部署与安全文档。
- 阿里云 Python API 已部署，`https://tthih.top/health` 返回 HTTP 200。
- 原 wstunnel 已停用，443 由 Nginx 接管。
- WinServer → 阿里云 HTTPS 已通过图形界面连通测试。
- 公司 Linux → WinServer → 阿里云全链路健康检查已返回 HTTP 200。
- 公司 Linux 上传数据已返回 HTTP 202。
- 阿里云创建任务后，公司 Linux 已成功轮询取得持久化任务。
- 2026-08-21 已修复 SQLite 写事务缺少 `commit()` 导致返回成功但数据回滚的问题。

本机实际验证结果：

- `/health`：HTTP 200。
- 无 Token 访问保护接口：HTTP 401。
- 数据上传：HTTP 202。
- 创建任务：HTTP 201。
- 轮询能够取得待处理任务。
- 结果回传：HTTP 200。
- 完成任务不会再次返回。
- Python 和 Shell 语法检查通过。
- `GET /api/tasks?limit=abc`：HTTP 400 `invalid_limit`。
- WinServer C# Release 编译通过（0 警告、0 错误）。
- `install-service.ps1` PowerShell 语法解析通过。

尚未完成：

- 完成一次正式的任务结果回传验收，并确认完成任务不再进入待处理列表。
- 生产级字段校验、日志、监控、备份和 Token 轮换。

## 11. 验收标准

- Linux能够完成健康检查、上传、轮询和结果回传。
- 错误 Linux Token 返回 401，非允许来源返回 403。
- 错误阿里云 Token 返回 401，未定义路径返回 404。
- 超大请求被拒绝。
- WinServer 无法访问配置外的域名、端口和路径。
- 阿里云 18082 不对公网监听，公网只暴露 Nginx HTTPS 443。
- 请求可通过 `X-Request-Id` 在三端关联。
- 服务重启后 SQLite 数据仍存在。
- 密钥不出现在源码、仓库、日志和聊天中。
- 方案获得公司网络与信息安全授权。

## 12. 推荐下一步

1. 回传已轮询到的测试任务结果，确认任务状态变为 `completed`。
2. 确认 WinServer 中继已安装为自动启动的 Windows 服务。
3. 建立 SQLite 备份、日志监控和 Token 轮换流程。
4. 上线正式业务前完成字段校验、数据保留策略和安全验收。

## 13. 常见问题

### 13.1 阿里云侧

- `curl http://127.0.0.1:18082/health` 连接失败：确认服务已启动、端口已监听、`server.py` 已安装。
- Nginx 找不到证书：确认证书路径是 `/etc/letsencrypt/live/tthih.top/`，不是旧的 `api.tthih.top`。
- 443 被占用：使用 `sudo ss -ltnp | grep ':443'` 查看；若为 wstunnel，停用 `wstunnel-server`。

### 13.2 WinServer 侧

- 双击 exe 报“无法从命令行或调试服务启动程序”：这是服务程序直接双击导致的，新版已改为双击打开图形界面；旧版请用管理员 PowerShell 运行 `.\AliyunApiRelay.exe console`。
- 「阿里云 API 地址必须是有效的 HTTPS URL」：地址必须写完整，例如 `https://tthih.top`，不能写 `tthih.top` 或 `http://...`。
- 「本端中继自检」提示未启动：先点「启动」，状态变为「运行中」后再测试。
- 中继转发仍用旧地址：修改地址后必须「保存配置」→「停止」→「启动」，让中继重新加载配置。
- 启动时拒绝访问：监听 80 端口需要管理员权限，或先执行 `install-service.ps1` 添加 URL ACL。

### 13.3 全链路侧

- 只有 Ubuntu 上执行 `./linux-client/relay-client.sh health` 返回 200，才算 Ubuntu → WinServer → 阿里云全链路打通。WinServer 图形界面的「本端中继自检」只是用 `127.0.0.1` 模拟测试。

## 14. 扩展其他服务的注意事项

当前 exe 不是通用代理，不能直接转发其他服务。如果后续需要新增转发对象，必须满足：

1. 明确服务类型：HTTP/HTTPS、TCP、WebSocket、端口、路径和方向。
2. 明确白名单：源地址、目标域名/IP、允许的 URL 路径。
3. 在代码中增加固定映射，而不是开放任意 URL。
4. 单独评估鉴权、请求大小、超时和日志。
5. 经公司网络和信息安全授权后再上线。

未经确认，不要把当前中继扩展成任意 TCP/URL 转发器，否则会把内网服务暴露到公网。
