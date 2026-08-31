# Company Linux Scan Worker

常驻公司 Linux，通过 WinServer 长轮询领取外网扫码指令，调用本机 `material-pull /api/scan/*`，再回传结果。

## 当前状态

源码和4组集成测试已完成。WinServer新版EXE、ApiToken和RelayToken均已配置，公司Linux到WinServer健康检查通过。`external-scan-worker.service` 已于2026-08-21安装、启用并启动，端到端只读 `preview` 指令执行成功并向阿里云回传 `SUCCEEDED`。

## 测试

```bash
python3 -m unittest discover -s tests -v
```

## 部署前提

1. WinServer已换成支持 `/aliyun-relay/worker/commands/*` 的新版EXE；
2. WinServer `ApiToken` 已与阿里云 `/etc/aliyun-api-relay/api.env` 中的 `RELAY_API_TOKEN` 对齐；
3. `SCAN_RELAY_TOKEN` 与WinServer `RelayToken`一致；
4. `SCAN_INTERNAL_DEVICE_KEY` 是独立强随机值；启用material-pull设备密钥校验时必须把它加入后端允许列表。

配置文件目标：`/etc/material-pull-external-scan/worker.env`，权限应为 `640 root:material-pull-worker`。

## 一键安装

在公司Linux执行：

```bash
cd /opt/apps/material-pull
sudo bash external-scan-relay/linux-worker/install-linux-worker.sh
```

脚本会创建低权限服务账号、生成内网设备密钥、写入受保护配置、注册并启动 `external-scan-worker.service`。

服务启动前先前台验证：

```bash
set -a
source /etc/material-pull-external-scan/worker.env
set +a
python3 worker.py
```
