# External Scan Cloud API

低配置阿里云上的轻量外网扫码指令中继。只负责设备登记、SQLite 指令队列、Worker 租约和结果查询，不执行 material-pull 业务。

## 本地测试

```bash
python3 -m unittest discover -s tests -v
```

## 接口

APP：

- `POST /app/v1/device/register`：使用 `X-Enrollment-Key` 登记或轮换设备凭证；
- `POST /app/v1/commands`：使用设备 Bearer Token 和 `X-Device-No` 提交指令；
- `GET /app/v1/commands/{requestId}`：查询本设备指令；
- `GET /app/v1/commands?limit=20`：查询本设备最近指令。

Worker：

- `POST /worker/v1/commands/claim?waitSeconds=15`；
- `POST /worker/v1/commands/{id}/renew`；
- `POST /worker/v1/commands/{id}/complete`；
- `POST /worker/v1/commands/{id}/fail`。

Worker 使用 `Authorization: Bearer <RELAY_API_TOKEN>` 和稳定的 `X-Worker-Id`。APP 与 Worker 不得共用凭证。

## 设备登记注意事项

`RELAY_APP_ENROLLMENT_KEY` 是受控发放码，不应硬编码进通用 APK。首次上线可由管理员在设备设置页临时输入；登记成功后 APP 只保存返回的独立 `deviceToken`，发放码应及时轮换。

## 部署前

1. 从 `api.env.example` 创建 `/etc/aliyun-api-relay/api.env` 并生成两条不同的随机凭证；
2. 将 `server.py` 安装到 `/opt/aliyun-api-relay/server.py`；
3. 安装 systemd 服务；
4. 安装 Nginx 配置并执行 `sudo nginx -t`；
5. 先验证本机 `/health`，再验证公网 HTTPS。
