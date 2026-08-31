# 外网 APP 扫码实现方式

> 第一阶段改造现有安卓 APP；未来如需微信小程序，可复用同一套云端指令协议。
## 一、建设目标

本次改造不是重新开发一套物料拉动系统，也不是把公司内网系统整体迁移到阿里云。

阿里云服务器性能较弱，因此阿里云只承担 HTTPS 接入、少量指令暂存和结果查询，不运行 material-pull、MySQL、Redis，也不执行物料业务计算。

目标是让处于外网的安卓 APP 能扫码。APP 在外网扫码后，最终仍调用公司 Linux 服务器上的现有物料拉动业务，使扫码结果、任务生成、状态变化、日志以及仓库页面表现与原扫码 App 一致。

需要保持的业务效果包括：

- 扫码预览与标签解析；
- 空盒扫码后生成补货任务；
- 到货、收货确认；
- 现场异常上报；
- 重复扫码拦截；
- 任务和盒子状态更新；
- 扫码日志、任务日志记录；
- 仓库页面和大屏实时提示。

## 二、总体架构

```text
外网安卓 APP 扫码
    ↓ HTTPS
阿里云 Nginx + 轻量 Python API + SQLite
    ↓ 保存待执行扫码指令
WinServer 固定路由中转服务
    ↑ 公司 Linux 主动长轮询
公司 Linux 扫码指令 Worker
    ↓ 调用本机 /api/scan/*
material-pull 现有后端
    ↓
生成任务、更新状态、写日志、推送仓库页面
    ↓ 执行结果原路返回
安卓 APP 按 requestId 查询并显示处理结果
```

阿里云不主动连接公司内网。跨网络请求继续采用已经打通的 Linux → WinServer → 阿里云链路，全部由公司内部主动发起。

## 三、现有系统直接复用的能力

### 3.1 扫码接口

| 业务 | 接口 | 用途 |
|---|---|---|
| 扫码预览 | `POST /api/scan/preview` | 解析标签并返回物料、工位等信息 |
| 空盒拉动 | `POST /api/scan/empty` | 生成补货任务并更新盒子状态 |
| 收货确认 | `POST /api/scan/receive` | 完成到货确认和任务闭环 |
| 异常上报 | `POST /api/scan/exception` | 记录异常并更新任务状态 |

小程序只负责扫码、提交指令和显示结果，不重新实现业务规则。实际业务继续由内网 `ScanService` 和 `TaskService` 完成。

### 3.2 现有业务能力

- 标签解析与物料映射；
- A/B 盒状态切换；
- 普通、紧急补货任务生成；
- 任务状态流转；
- 库存、打印、AGV 和空盒回收联动；
- 扫码日志和任务审计日志；
- WebSocket/Redis 实时推送；
- 业务锁、重复扫码拦截和幂等处理。

## 四、安卓 APP 第一阶段功能

1. 微信登录和员工身份绑定；
2. 调用微信扫码功能获取二维码原文；
3. 扫码预览；
4. 空盒拉动；
5. 收货确认；
6. 异常上报；
7. 显示等待、处理中、成功和失败状态；
8. 显示任务号、物料、仓库、配送地址和错误信息；
9. 查看本人最近的扫码记录。

外网模式下，APP 不得保存公司内网 API Key 或 Worker Token，也不得直接访问公司 Linux 服务器。

## 五、扫码指令设计

阿里云需要把每次操作保存为可追踪的扫码指令，至少包含：

| 字段 | 说明 |
|---|---|
| `requestId` | 全链路唯一请求号，用于幂等和结果查询 |
| `action` | `preview`、`empty`、`receive` 或 `exception` |
| `scanCode` | 微信扫码得到的原始内容 |
| `userId` | 阿里云中的小程序用户编号 |
| `employeeNo` | 员工工号 |
| `deviceNo` | 小程序设备标识 |
| `status` | 指令当前状态 |
| `createdAt/expiresAt` | 创建时间和过期时间 |
| `attemptCount` | 执行尝试次数 |
| `result/error` | 内网返回的结果或错误 |

建议状态：

```text
QUEUED      已提交，等待公司 Linux 领取
PROCESSING  已领取，正在执行
SUCCEEDED   内网业务处理成功
FAILED      业务执行失败
EXPIRED     超过有效期仍未执行
```

小程序提交示例：

```json
{
  "requestId": "wxscan-20260821-uuid",
  "action": "empty",
  "scanCode": "13799816,物料架-01-F03,备用",
  "employeeNo": "123456",
  "deviceNo": "WX-MINI-PROGRAM",
  "requestQty": 1,
  "requestUnit": "个",
  "allowRepeat": false
}
```

Linux Worker 调用本机后端时，应把同一个 `requestId` 作为 `X-Idempotency-Key` 和请求体中的 `idempotencyKey`，确保网络重试不会重复创建补货任务。

## 六、需要新增的程序

### 6.1 阿里云 Nginx + 轻量 Python API + SQLite

- 微信 `code2Session` 登录与用户会话；
- `openid` 和员工工号绑定；
- 用户、工厂、工位及操作权限；
- 扫码指令提交接口；
- 按 `requestId` 查询处理结果；
- 查询本人最近扫码记录；
- 指令原子领取、执行租约、超时重试和过期处理；
- 请求校验、频率限制和审计日志。

### 6.2 公司 Linux Worker

目前 Linux 端只有手工测试脚本 `relay-client.sh`。正式使用需要增加 systemd 常驻 Worker，负责：

- 定时轮询阿里云；
- 原子领取待执行指令；
- 校验操作类型和字段；
- 转换并调用现有 `/api/scan/*`；
- 使用内部 API Key 调用 material-pull；
- 回传完整成功结果或业务错误；
- 网络失败时安全重试；
- 记录指令号、时间和结果，但不记录 Token。

Worker 建议使用 10～15 秒长轮询，而不是每秒发送一次空请求。有任务时立即返回；无任务时最多等待 15 秒。APP 查询超时后必须继续查询原 requestId，不能自动创建新指令。

## 七、当前中转代码需要完善的问题

1. **GET 查询参数未透传**：WinServer 当前不会转发 `/tasks?limit=50` 中的查询参数。
2. **任务可能重复领取**：阿里云只查询 `pending`，没有原子领取和执行租约。
3. **任务状态不足**：目前只有 `pending/completed`，无法表示处理中、失败、超时和过期。
4. **Linux 没有常驻执行器**：当前脚本只能手工测试，不能持续处理扫码指令。
5. **Outbox 不能直接用于外网同步**：payload 不保证为标准 JSON，也没有发送、确认和重试闭环。

## 八、安全要求

1. 小程序只保存自己的登录会话，不保存任何机器密钥；
2. Linux → WinServer 使用 `X-Relay-Token`；
3. WinServer → 阿里云使用 Bearer Token；
4. Linux Worker → material-pull 使用单独的内部 API Key；
5. 三类密钥必须不同，不得写入源码或小程序；
6. 阿里云必须校验用户身份、操作类型、工厂和工位权限；
7. 扫码指令必须设置有效期，禁止执行过期指令；
8. 不信任小程序直接上传的员工身份、角色和权限信息；
9. 日志不得输出 Token、微信 session key 等敏感信息。

## 九、实施顺序

1. **完善中转协议**：确定指令结构，增加原子领取、租约、完整状态和查询参数透传。
2. **开发 Linux Worker**：调用现有扫码接口，处理幂等、重试、超时及结果回传。
3. **开发阿里云小程序接口**：完成微信登录、员工绑定、权限和指令查询。
4. **开发小程序页面**：完成扫码、确认、处理进度及结果展示。
5. **联调验收**：验证正常扫码、重复提交、断网恢复、错误显示、任务页面和日志一致性。

## 十、最终结论

外网 APP 只是新的扫码交互入口，不替代公司内网业务后端。未来如增加微信小程序，也复用同一套中继协议。

扫码解析、重复校验、任务生成、盒子切换、库存、打印、AGV、日志和仓库实时提示等核心业务，仍由现有 material-pull 系统处理。阿里云和 WinServer 只负责安全传递扫码指令并返回执行结果。

这样既能获得与原扫码 App 一致的业务效果，也不需要向公网开放公司内网的入站访问。

## 十一、正式技术方案补充（2026-08-21）

### 11.1 低配置阿里云的职责边界

阿里云只作为轻量“指令收发箱”：继续扩展 `external-scan-relay/cloud-api/server.py`，使用 Python 标准库、SQLite 和 Nginx。物料解析、数据库查询、任务生成、状态联动和实时推送全部留在公司内网。

- SQLite 启用 WAL、`busy_timeout` 和短事务；
- 成功记录建议保留 7 天，失败记录保留 30 天；
- Nginx 负责 HTTPS、限流、请求体限制和超时；
- 不向阿里云同步物料主数据、员工密码或公司数据库；
- 不在阿里云部署 Java 后端、MySQL、Redis 或业务定时任务。

### 11.2 正式接口

APP 公网接口：

```http
POST /app/v1/device/register
POST /app/v1/commands
GET  /app/v1/commands/{requestId}
GET  /app/v1/commands?mine=true&limit=20
```

Worker 服务接口：

```http
POST /worker/v1/commands/claim?waitSeconds=15
POST /worker/v1/commands/{requestId}/renew
POST /worker/v1/commands/{requestId}/complete
POST /worker/v1/commands/{requestId}/fail
```

APP 提交接口只负责鉴权、校验和幂等写入 SQLite，应立即返回 `requestId` 和 `QUEUED`，不能占用阿里云请求线程同步等待内网处理完成。APP 可每 500 毫秒至 1 秒查询结果，前台最多等待 20～30 秒；超时后保存原 `requestId` 并稍后继续查询。

### 11.3 指令状态机和租约

```text
QUEUED
  → LEASED
      → SUCCEEDED
      → FAILED
      → 租约超时后重新进入 QUEUED
  → EXPIRED
```

领取必须在一个 SQLite 短事务内完成“查询 + 更新为 `LEASED`”，避免多个 Worker 取得同一指令。建议租约 30 秒、最多执行 3 次。指令表应包含 `lease_owner`、`lease_until`、`attempt_count`、`expires_at`、`completed_at`、`result` 和 `error`。

APP 对同一次操作重试时必须复用原 `requestId`。Worker 调用内网时，将该值同时放入 `X-Idempotency-Key` 和请求体 `idempotencyKey`，避免重复建单。

### 11.4 WinServer 固定路由

```text
/aliyun-relay/worker/commands/claim
  → /worker/v1/commands/claim
/aliyun-relay/worker/commands/{id}/renew
  → /worker/v1/commands/{id}/renew
/aliyun-relay/worker/commands/{id}/complete
  → /worker/v1/commands/{id}/complete
/aliyun-relay/worker/commands/{id}/fail
  → /worker/v1/commands/{id}/fail
```

WinServer 必须透传 `claim?waitSeconds=15` 的查询参数。不得将 `/aliyun-relay/*` 改成任意路径通配转发。

Worker 使用 10～15 秒长轮询。以 15 秒为例，空闲请求量可从每天约 86400 次降为约 5760 次；有任务时服务端立即返回。当前 WinServer 上游超时默认 20 秒，因此长轮询不应超过 15 秒。

### 11.5 安卓 APP 双模式

| 模式 | 地址 | 调用方式 |
|---|---|---|
| 内网直连 | `http://10.243.129.131/api` | 同步调用现有 `/scan/*` |
| 外网中继 | `https://tthih.top/app/v1` | 异步提交并按 `requestId` 查询 |

外网模式需要每台设备使用独立、可撤销的设备凭证；本地保存未完成请求；显示排队中、内网处理中、成功、失败和等待超时；只允许 HTTPS；预览成功后仍使用原始扫码内容正式提交。

### 11.6 四段独立鉴权

| 凭证 | 方向 | 用途 |
|---|---|---|
| APP 设备凭证 | APP → 阿里云 | 每台设备独立鉴权和撤销 |
| `RelayToken` | Linux → WinServer | `X-Relay-Token` |
| `ApiToken` | WinServer → 阿里云 | Worker 接口 Bearer Token |
| `X-Device-Key` | Worker → material-pull | 内网扫码接口专用凭证 |

四类凭证必须不同，不得写入源码、日志、README、截图或硬编码进通用 APK。APP 接口应按设备限流，例如每分钟最多 30 次；指令请求体限制为 16～64 KiB，结果体限制为 256 KiB；过期指令禁止执行。

## 十二、实施顺序与当前下一步

1. 改造阿里云轻量 API：增加 APP/Worker 接口、SQLite 迁移、原子领取、租约、幂等、重试、过期和自动化测试；先不替换线上服务。
2. 开发 Linux Worker：实现长轮询、内网调用、幂等、续租、重试和结果回传，配置为 systemd 服务。
3. 扩展 WinServer 固定路由：增加 Worker 白名单路由和查询参数透传，重新编译并测试。
4. 改造安卓 APP：增加内网直连/外网中继模式、设备鉴权、异步查询和未完成请求恢复。
5. 完成安全配置与全链路联调，覆盖重复提交、Worker 崩溃、回传断网、APP 退出恢复、过期指令、错误鉴权、限流和数据清理。

当前首先改造：

```text
/opt/apps/material-pull/external-scan-relay/cloud-api/server.py
```

先完成源代码、SQLite 迁移和本地自动化测试，不直接改动线上部署。验证通过后，再依次开发 Worker、扩展 WinServer 和改造 APP。

## 十三、最终结论

外网 APP 只是新的扫码入口，不替代公司内网业务后端。阿里云只承担低负载的指令暂存和结果查询，WinServer 只负责固定路由安全转发，核心业务仍由现有 `material-pull` 处理。

该方式可以同时解决外网扫码、公司内网不开放入站访问和阿里云服务器性能较弱三个问题。
