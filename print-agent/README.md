# 斑马标签自动打印代理 (AutoPrintAgent)

连接 USB 斑马打印机（如 GT800）的 Windows 电脑上运行的本地代理。它定时向后端领取待打印标签，用 RAW 方式把 ZPL 直接发给打印机，实现「后台出现待打印标签 → USB 打印机自动出标签」的全自动效果。

不依赖 Python/Node，基于 .NET Framework 4.7.2（Win10/Win11 自带），带配置界面，可最小化到托盘静默运行。

## 工作原理（拉模式）

```
后台生成标签任务
      │
      ▼
Ubuntu 后端写入 t_print_job 表（状态 RENDERED，含 ZPL）
      │
      ▼   本代理每隔几秒轮询
GET /api/print-jobs/next        领取一批作业（原子置为 SENT）
      │
      ▼
RAW 发 ZPL 到 USB 打印机（不弹窗、不走驱动排版）
      │
      ▼
POST /api/print-jobs/callback   回传 PRINTED / FAILED
      │
      ▼
后台“出货标签打印 / 打印作业”显示结果
```

代理领取后若崩溃/断网未回传，后端在 `printClaimStaleSeconds`（默认 120 秒）后会把该作业重新分配，避免标签永远打不出来。

## 配置项（界面上可设）

- 服务器地址：后端 API 根地址，如 `http://<服务器IP>/api`
- API 密钥：对应后端 `app.security.external-api-key`
- 打印机：本机安装的打印机名（下拉可选），留空则用作业自带打印机名
- 仅取此打印机：多台打印机分工时，只领取指定打印机名的作业
- 轮询间隔（秒）、每次最多领、失败重试次数
- 定时时段：仅在设定时段内自动打印（支持跨零点）
- 程序启动后自动开始轮询
- 开机自动启动本程序（写当前用户注册表 Run 项）

配置保存在 exe 同目录 `config.json`，日志在 `logs\agent-YYYY-MM-DD.log`。

## 构建

在装有 .NET SDK 或 Visual Studio 的机器上：

```bat
build.bat
```

或手动：

```bat
dotnet publish AutoPrintAgent.csproj -c Release -r win-x64 --self-contained false -o dist
```

输出 `dist\AutoPrintAgent.exe`，把整个 `dist` 拷到打印电脑即可。

> 打印电脑本身不需要装 SDK，只要有 .NET Framework 4.7.2（Win10 1803+/Win11 均自带）。

## 部署到打印电脑

1. 拷贝 `dist` 到打印电脑，例如 `C:\AutoPrintAgent\`
2. 双击 `AutoPrintAgent.exe`
3. 填写服务器地址、API 密钥，选择打印机
4. 点「测试连接」确认能访问后端
5. 点「保存设置」，再点「开始轮询」
6. 勾选「开机自动启动」和「启动后自动开始轮询」，即可全自动

关闭窗口会最小化到托盘继续运行，右键托盘图标可「退出」。

## 可选：注册为 Windows 服务（无人值守更稳）

用 [NSSM](https://nssm.cc/) 把它做成服务，开机自启、崩溃自动重启：

```bat
nssm install AutoPrintAgent "C:\AutoPrintAgent\AutoPrintAgent.exe"
nssm set AutoPrintAgent AppDirectory "C:\AutoPrintAgent"
nssm set AutoPrintAgent Start SERVICE_AUTO_START
nssm start AutoPrintAgent
```

> 注意：作为服务运行在 Session 0，托盘图标/界面不可见。建议先用界面模式调通配置（生成 `config.json`），再装服务。卸载：`nssm remove AutoPrintAgent confirm`。

## 先验证 GT800 是否认 ZPL

GT800 有 ZPL 和 EPL 两种固件。用「立即打印一次」或下面的 PowerShell 测一张：

```powershell
$zpl = "^XA^FO50,50^A0N,40,40^FDZPL TEST^FS^XZ"
$bytes = [System.Text.Encoding]::ASCII.GetBytes($zpl)
$printer = "ZDesigner GT800 (EPL)"   # 改成你的准确打印机名
# 通过共享名 RAW 打印，或直接用本代理的“立即打印一次”
```

- 能打出 `ZPL TEST` → 是 ZPL 固件，直接用。
- 打出乱码 → 需在打印机首选项切换语言为 ZPL，或改造后端生成 EPL 指令。

## 后端相关配置（application.yml / 环境变量）

```yaml
app:
  factory:
    print-pull-mode: true          # 拉模式（本代理），默认已开启
    print-claim-stale-seconds: 120 # 领取后多久未回传视为超时可重发
  security:
    external-api-key: 你的密钥      # 代理用它鉴权，务必改掉默认值
```

- 拉模式下后端不再主动推送打印，作业停在 `RENDERED` 等代理领取。
- 若要回到旧的推模式：`print-pull-mode: false` 并配置 `print-submit-url`。
