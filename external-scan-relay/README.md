# 外网APP扫码完整方案 - 公司Linux—WinServer—阿里云中继

更新时间：2026-08-24

## 项目概述

本项目实现了外网安卓APP通过扫码与公司内网物料拉动系统的交互。由于公司Linux服务器**不能直接访问公网**（会被防火墙拦截），因此采用**WinServer中转架构**。

## 系统架构

```
安卓APP (外网)
    ↓ HTTPS
阿里云API服务 (tthih.top:443)
    ↓ MySQL/SQLite
阿里云数据库
    ↑ HTTP + Bearer ApiToken (通过WinServer中转)
WinServer中转 (10.243.111.152:80)
    ↑ HTTP + X-Relay-Token (内网)
Linux Worker (公司内网 10.243.129.131)
    ↓ HTTP (内网)
内网material-pull后端
```

**关键特点：**
- 复用现有WinServer中转架构
- 不需要Linux直接访问公网（避免防火墙拦截）
- 所有公网访问都由WinServer发起
- 使用两段独立鉴权：X-Relay-Token（Linux→WinServer）和 Bearer ApiToken（WinServer→阿里云）

## 当前项目状态

✅ **已完成：**
- 内网Spring Boot后端新增 `DeviceController.java`
- 阿里云API服务已部署到 `tthih.top`
- WinServer中转已运行并支持外网扫码指令接口
- Linux Worker已安装为systemd服务 `external-scan-worker.service`
- 真实 `preview` 指令已完成全链路闭环测试，状态为 `SUCCEEDED`
- SQLite写事务提交问题已修复（2026-08-21）
- 结果回传接口已实现

## 项目目录

```
external-scan-relay/
├── README.md                # 本文档
├── cloud-api/               # 阿里云Python API、SQLite、systemd、Nginx
├── win-relay/               # WinServer C# 中继源码和可执行文件
├── linux-client/            # 通用链路手工测试客户端
├── linux-worker/            # 外网扫码常驻Worker、systemd与测试
└── docs/
    ├── external-android-scan.md    # 外网扫码方案详解
    └── relay-deployment.md          # 部署说明
```

## 固定接口

| Linux 调用 WinServer | WinServer 转发到阿里云 | 用途 |
|---|---|---|
| `GET /aliyun-relay/health` | `GET /health` | 全链路健康检查 |
| `POST /aliyun-relay/data` | `POST /api/data` | 上传业务数据 |
| `GET /aliyun-relay/tasks` | `GET /api/tasks` | 轮询待处理任务 |
| `POST /aliyun-relay/tasks/{id}/result` | `POST /api/tasks/{id}/result` | 回传任务结果 |

阿里云本机另有 `POST /api/tasks` 用于创建任务。WinServer不向Linux暴露该接口。

## 两段独立鉴权

| Token | 方向 | HTTP 请求头 | 配置位置 |
|-------|------|------------|---------|
| `RelayToken` | Linux → WinServer | `X-Relay-Token` | Linux Worker `.env` |
| `ApiToken` | WinServer → 阿里云 | `Authorization: Bearer` | WinServer `relay-config.json` + 阿里云 `api.env` |

两个Token必须不同且至少32个字符。

**当前使用的 RelayToken（Linux → WinServer）：**
```
11111111111111111111111111111111
```
该值由项目负责人明确决定继续使用。WinServer `ApiToken` 仅保存在 WinServer 配置与阿里云环境文件中，不在本文档记录。

## 完整部署指南

### 第一步：阿里云部署（已完成 ✅）

阿里云服务器已重置为 Ubuntu 24.04，Nginx、Python、SQLite 和 HTTPS 证书已准备就绪。

**部署位置：** `/opt/aliyun-api-relay/server.py`  
**数据库位置：** `/var/lib/aliyun-api-relay/relay.db`  
**配置位置：** `/etc/aliyun-api-relay/api.env`

#### 更新阿里云服务

本地源文件位置：
```
/opt/apps/material-pull/external-scan-relay/cloud-api/server.py
```

更新步骤：
```bash
# 复制更新文件到阿里云
scp cloud-api/server.py root@tthih.top:/opt/aliyun-api-relay/

# 在阿里云服务器上重启服务
sudo systemctl restart aliyun-api-relay
sudo systemctl status aliyun-api-relay --no-pager
```

#### 测试阿里云API
```bash
curl https://tthih.top/api/health
```

### 第二步：WinServer配置（已完成 ✅）

WinServer中转已运行支持外网扫码指令接口的新版EXE。

**部署位置：** `%ProgramFiles%\AliyunApiRelay`  
**配置文件：** EXE同目录的 `relay-config.json`

WinServer只允许 `10.243.129.131` 访问中继TCP 80端口（Windows防火墙规则）。

### 第三步：Linux Worker部署（已完成 ✅）

#### 一键安装
```bash
cd /opt/apps/material-pull
sudo bash external-scan-relay/linux-worker/install-linux-worker.sh
```

当前 `external-scan-worker.service` 已启用并运行。重复执行安装脚本可重新部署配置与服务。

#### 手动配置
配置位置：`/etc/aliyun-api-relay/client.env`

```bash
# 查看服务状态
sudo systemctl status external-scan-worker

# 查看日志
sudo journalctl -u external-scan-worker -f

# 重启服务
sudo systemctl restart external-scan-worker
```

### 第四步：内网后端（已完成 ✅）

内网后端已新增 `DeviceController.java` 并重新编译部署。

```bash
cd /opt/apps/material-pull/backend
mvn clean package -DskipTests
sudo systemctl restart material-pull-backend.service
```

版本号：`0.9.2`

### 第五步：安卓APP配置

在APP设置界面修改服务器地址：
```
服务器地址: https://tthih.top/api
```

点击"测试"验证连接，然后"保存"。

## 测试验证

### 1. 测试全链路健康检查
```bash
cd /opt/apps/material-pull/external-scan-relay/linux-client
set -a
source /etc/aliyun-api-relay/client.env
set +a

bash relay-client.sh health
```

### 2. 测试数据上传
```bash
bash relay-client.sh send payload.example.json
```

### 3. 测试任务轮询
```bash
bash relay-client.sh tasks
```

### 4. 测试结果回传
```bash
printf '%s\n' '{"success":true,"output":"hello"}' > /tmp/result.json
bash relay-client.sh result TASK_ID /tmp/result.json
```

### 5. 测试APP扫码

1. 打开APP，扫描物料标签
2. 观察显示"正在查询物料"
3. 1-3秒后显示物料信息
4. 点击"发送"
5. 1-3秒后显示"发送成功"

同时观察Linux Worker日志：
```bash
sudo journalctl -u external-scan-worker -f

# 应该看到：
# 领取指令: xxx | preview | 扫码内容
# 执行成功: xxx
# 回传完成: xxx
```

## 数据流向详解

```
1. 用户在APP扫码
   APP → 阿里云API → 保存指令到SQLite (status=QUEUED)

2. Linux Worker轮询（每1秒）
   Worker → WinServer → 阿里云API → 查询指令
   
3. 领取指令
   阿里云API返回指令 → WinServer → Worker
   指令状态变为 PROCESSING
   
4. 执行业务
   Worker → 内网material-pull API → 生成任务
   
5. 回传结果
   Worker → WinServer → 阿里云API → 更新状态(SUCCEEDED/FAILED)
   
6. APP收到结果
   阿里云API返回结果 → APP显示
```

**总耗时：** 1-3秒

## Linux 使用方法

### 手动测试
```bash
cd /opt/apps/material-pull/external-scan-relay/linux-client
set -a
source /etc/aliyun-api-relay/client.env
set +a

bash relay-client.sh health
bash relay-client.sh send payload.example.json
bash relay-client.sh tasks
```

### 阿里云创建测试任务

先将systemd环境文件加载到当前shell：
```bash
set -a
source /etc/aliyun-api-relay/api.env
set +a
```

然后创建任务：
```bash
curl --fail-with-body \
  -H "Authorization: Bearer $RELAY_API_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"type":"preview","value":"test-material-code"}' \
  http://127.0.0.1:18082/api/tasks
```

不要使用 `echo "$RELAY_API_TOKEN"`，避免Token出现在终端记录或截图中。

## 常见问题排查

### `401 invalid_token`
Linux使用的必须是WinServer中配置的 `RelayToken`，不是 `ApiToken`，也不能使用示例占位文字。

### 阿里云本机创建任务返回 `401 unauthorized`
systemd的环境变量不会自动进入当前shell。先执行：
```bash
set -a
source /etc/aliyun-api-relay/api.env
set +a
```

### 创建任务成功但Linux查询为空
旧版 `server.py` 缺少SQLite `commit()`，会在连接关闭时回滚。2026-08-21版本已经在数据上传、创建任务和结果回传三个写操作中提交事务。确认阿里云运行的是最新的 `/opt/aliyun-api-relay/server.py` 并重启服务。

### WinServer测试返回200，但业务仍未验证
`/health` 用于连通性检查。还应测试 `/api/data` 和任务创建/轮询，才能确认Token、转发与SQLite持久化全部正常。

### Worker日志显示"无法连接到服务器"
检查WinServer是否运行：
```bash
curl http://10.243.111.152/aliyun-relay/health -H "X-Relay-Token: 11111111111111111111111111111111"
```

### Worker日志显示"认证失败（401）"
检查Linux Worker的 `RELAY_TOKEN` 是否与WinServer的 `RelayToken` 一致（不要用ApiToken）。

### APP显示"处理超时"
Worker未运行或无法处理指令。检查：
```bash
sudo systemctl status external-scan-worker
sudo journalctl -u external-scan-worker -n 50
```

## 性能指标

| 指标 | 数值 |
|-----|------|
| APP扫码响应时间 | 1-3秒 |
| Worker轮询间隔 | 1秒 |
| 指令超时时间 | 30秒 |
| 支持并发设备 | 10-20台 |

## 安全与运维

- Windows防火墙只允许 `10.243.129.131` 访问中继TCP 80
- 阿里云Python API只监听 `127.0.0.1:18082`
- 公网只暴露Nginx HTTPS 443
- 配置文件仅允许管理员和服务账户读取
- 定期轮换两个Token
- 定期备份 `/var/lib/aliyun-api-relay/relay.db`
- 配置Nginx和systemd日志轮换、监控和告警
- 上线正式业务前补充JSON字段校验、数据保留周期和审计策略
- 所有使用方式应符合公司的网络与信息安全规定

## 成本估算

**阿里云费用（月）：**
- 使用现有ECS服务器：0元（已有）
- SQLite数据库：0元（无需RDS）
- 流量费：约10元

**总计：约10元/月**

## 完成清单

- [x] 阿里云API部署并启动
- [x] 阿里云数据库（SQLite）初始化
- [x] Nginx配置更新
- [x] WinServer中转支持新路由
- [x] Linux Worker部署并启动
- [x] 内网后端重新编译（版本0.9.2）
- [x] 全链路测试通过
- [ ] APP配置服务器地址（需用户手动操作）
- [ ] 生产环境正式上线

## 历史背景

公司Linux直连阿里云时，HTTPS会被企业透明代理处理；WebSocket收到HTTP 302，HTTP/2隧道出现 `GOAWAY / PROTOCOL_ERROR`，SSH协议特征也会被识别拦截。因此最终采用WinServer应用层中继和标准HTTPS API，不再以wstunnel作为Linux业务通信方案。

本项目是固定接口的业务中继，不是通用代理，不提供SSH、WebSocket、任意TCP或任意URL转发。

## 下一步行动

1. ✅ 系统已部署完成
2. ⏳ APP配置服务器地址（需用户操作）
3. ⏳ 生产环境测试
4. ⏳ 建立日志监控和告警机制
5. ⏳ 制定Token轮换计划

## 文档索引

- **外网扫码方案：** `docs/external-android-scan.md`
- **部署详细说明：** `docs/relay-deployment.md`
- **Linux Worker测试：** `linux-worker/README.md`
- **阿里云API说明：** `cloud-api/README.md`

## 需要帮助？

如有问题，请查看：
1. 相关文档中的"故障排查"章节
2. 查看各组件的日志输出
3. 验证Token配置是否正确

**重要提醒：** 所有真实的Token不要出现在代码、文档、截图或聊天中！
