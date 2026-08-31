# WinServer Relay

.NET Framework 4.7.2固定路由中继。当前源码已增加外网扫码Worker路由，但仓库中的旧EXE尚未重新编译。

## 新增固定路由

```text
POST /aliyun-relay/worker/commands/claim?waitSeconds=15
POST /aliyun-relay/worker/commands/{id}/renew
POST /aliyun-relay/worker/commands/{id}/complete
POST /aliyun-relay/worker/commands/{id}/fail
```

中继会透传查询参数、`X-Request-Id`和`X-Worker-Id`，并继续使用自己的Bearer `ApiToken`访问阿里云。

## Windows编译

在装有Visual Studio Build Tools或.NET SDK的WinServer源码目录执行：

```powershell
dotnet build .\AliyunApiRelay.csproj -c Release
```

先备份当前EXE和`relay-config.json`，不要覆盖配置文件。新EXE在测试目录启动后，验证旧health路由、新claim路由以及未知路径404，再安排替换。

阿里云重置后生成了新 `ApiToken`。请在阿里云终端本地查看并直接填入WinServer界面，不要复制到聊天或文档：

```bash
sudo awk -F= '$1=="RELAY_API_TOKEN" {print $2}' /etc/aliyun-api-relay/api.env
```
