# Ubuntu 业务系统对外访问方案（整合版）

整理日期：2026-07-18
状态：**已解决并上线**。最终方案 = Ubuntu 本机 Nginx 监听 80 端口对外，各网段直连，无需 Windows Server 中转、无需隧道、无需 TLS。

> 本文档整合此前全部分析、排查与实施记录，是唯一权威版本，可随项目一起放入 Ubuntu 仓库。
> 原始截图与网页存档保留在 `Windows Server 管理界面_files/` 与 `Windows Server 管理界面.html`，作为证据备查。

---

## 结论速览（TL;DR）

- **根因**：Windows 与 Ubuntu 跨网段，中间一台公司有状态安全设备**按目标端口白名单拦截**——标准 Web 端口 **80 放行**，非标准端口（8000/8443/8080 等）在应用数据传输阶段一律注入 RST 掐断。与方向、是否加密无关。
- **决定性验证**：Ubuntu 出站到 Windows:80 拿到完整响应；而 :8080 被 RST。反向从各网段访问 Ubuntu:80 也完整可达。
- **最终架构**：Ubuntu 装 Nginx 监听 **80**，前端静态文件 + `/api` 同源反代到本机 `127.0.0.1:8080` 后端。各网段用户直接访问 `http://<Ubuntu-IP>/`。
- **收益**：不经 Windows、不用隧道/TLS；前后端同源，**CORS 问题自然消失**。
- **抗 IP 变化**：服务端 `server_name _` + 反代 `127.0.0.1`，换 IP 零改动；另有开机自启的“访问地址播报”服务，IP 变化时自动把最新地址写到家目录 `物料拉动系统-访问地址.txt`。

### 一键部署（新机器/重装）

```bash
# 1. 构建前端
cd /home/tthih/IE/frontend && npm run build
# 2. 启动后端（8080）
bash /home/tthih/IE/deploy/scripts/start.sh backend
# 3. 安装 Nginx 80 入口（需 root）
sudo bash /home/tthih/IE/deploy/scripts/setup-nginx-gateway.sh
# 4. 安装访问地址播报（需 root，可选）
sudo cp /home/tthih/IE/deploy/systemd/material-pull-url-announcer.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now material-pull-url-announcer.service
```

管理员默认账号 `admin`，密码见 `deploy/scripts/material-pull.env` 的 `MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD`。

---

## 一、项目目标

将部署在 Ubuntu 上的业务系统（Spring Boot 后端 + Vue 前端 + 数据库）对外提供访问，入口经由公司 Windows Server 反向代理，且：

- 不停止、不修改、不破坏 Windows Server 上现有生产业务。
- Ubuntu 不直接向所有公司网段暴露。
- 项目本体（应用、数据库、依赖）全部部署在 Ubuntu，Windows Server 只做 HTTPS 入口 + 反向代理。

理想访问链路：

```text
客户端
  → Windows Server HTTPS 443
  → Ubuntu 10.243.129.77:业务端口
  → Ubuntu 上的网页服务
```

---

## 二、环境信息

| 设备 | 网卡 | IP | 网关 | ifIndex |
|---|---|---|---|---|
| Windows Server | Embedded NIC 1 | 10.243.111.152/24 | 10.243.111.1 | 12 |
| Windows Server | Embedded NIC 2 | 192.168.110.69/24 | 192.168.110.1 | 7 |
| Ubuntu | enp0s31f6 | 10.243.129.77/24 | 10.243.129.1 | - |

- 两台主机不在同一网段，靠中间一台公司网络设备做三层转发。
- Windows Server 已运行生产业务：IIS 站点 `website`、SQL Server(1433)、MySQL(3306)、Tomcat(8080/8009)、SUNPN PC SERVER、文件服务(6688)、其它(9090) 等。
- Windows 防火墙关闭；360netmon 与 CrowdStrike 均注册了 WFP 网络过滤器。
- Windows 已安装 OpenSSH Server（可按需临时监听）。

---

## 三、连接障碍排查与定案

### 1. 排查方法学
- `TcpTestSucceeded: True` 只证明 TCP 三次握手成功，不代表 HTTP/SSH 数据能完整传输。异常都发生在应用数据开始传输之后。
- 在 HTTP 完整可用前配置 IIS ARR 没有意义。
- 有效方法：Ubuntu 同时运行服务日志与 tcpdump，Windows 只发一次请求，做受控测试。

### 2. 已排除的误判
- `curl.exe 无法识别`：Windows Server 2016 未自带 `curl.exe`，是命令不存在，非拦截。改用 `Invoke-WebRequest`。
- Ubuntu `nc -vz 10.243.111.152 22` 返回 `Connection refused`：正常，当时 Windows sshd 未监听。
- 端口测错：Ubuntu 实际监听 18000，Windows 曾误测 8080，后统一。

### 3. 第一个根因（已解决）：路由走错网卡
`Find-NetRoute -RemoteIPAddress 10.243.129.77` 曾显示流量走 NIC 2（源 192.168.110.69、命中默认路由 0.0.0.0/0），且精确路由丢失（`route add` 为非持久，重启即消失）。

「时通时断」真相：有精确路由时走 NIC 1（通），精确路由丢失后退回默认路由走 NIC 2（不通）。与安全软件无关。

修复（临时、可回退）：

```powershell
route add 10.243.129.77 mask 255.255.255.255 10.243.111.1 metric 1 if 12
```

修复后：`InterfaceAlias: Embedded NIC 1`、`SourceAddress: 10.243.111.152`、`TcpTestSucceeded: True`。

### 4. 核心障碍（未解决）：中间设备注入 RST
路由修好后，HTTP 仍失败。Ubuntu 抓包显示完整会话：GET 到达、Ubuntu 完整返回 200（响应头 156B + 响应体 1371B），但响应到达瞬间连接被 RST 掐断。

### 5. 决定性证据：TTL 比对

| 包 | 方向 | Flags | TTL |
|---|---|---|---|
| SYN / ACK / GET | Windows → Ubuntu | [SEW]/[.]/[P.] | **127** |
| RST | 伪装 Windows → Ubuntu | [R.] | **63** |

- Windows 初始 TTL=128（观测 127）。
- RST 初始 TTL=64（观测 63），是 Linux/BSD 内核特征。
- 两者初始 TTL 不同，**RST 绝非 Windows 本机发出**，而是中间一台独立网络设备伪装成客户端 IP 注入。

### 6. 定案（2026-07-18 新网段复测后更正）
连接失败由两个独立问题叠加：
1. 路由问题（已解决，见下方“新网段补充”）。
2. **中间有状态安全设备按目标端口拦截**：对非标准端口的应用数据注入 RST；标准 80 端口放行（核心，已通过换端口绕过）。

**已排除**：360netmon、CrowdStrike、Windows 本机 WFP、路由（已修）、非对称路由假设。

该设备属公司网络基础设施，无法本地登录或配置。因此在 Windows/Ubuntu 端点调整 IIS、安全软件或路由都无法改变其拦截行为——但可通过**只走它放行的 80 端口**绕过。

### 7. 新网段补充实测（Ubuntu 迁移到 10.242.36.66 后）

迁移后 Ubuntu IP 变为 `10.242.36.66`（网卡 `enp0s31f6`），与 Windows `10.243.111.152` 仍跨网段。复测记录：

- **路由走错网卡复发**：`Find-NetRoute` 显示去 `10.242.36.66` 走了 NIC 2（源 192.168.110.69）。因旧 /32 路由是针对旧 IP `10.243.129.77` 的，对新 IP 无效。
  修复（临时、可回退）：`route add 10.242.36.66 mask 255.255.255.255 10.243.111.1 metric 1 if 12`
- **分层受控测试**，规律高度一致：

| 测试 | 方向 | 端口 | TCP握手 | 应用数据 | 结果 |
|---|---|---|---|---|---|
| 明文 HTTP | Win→Ubuntu 入站 | 18000 | 通 | GET到达、Ubuntu返回200 | 响应被 RST |
| TLS | Win→Ubuntu 入站 | 18443 | 通 | ClientHello 发送时 | 被 RST |
| HTTP | Ubuntu→Win 出站 | 8080 | 通 | 等响应时 | 被 RST |
| HTTP | Ubuntu→Win 出站 | **80** | 通 | 完整 404 响应（315B，连测3次稳定） | **✅ 放行** |
| HTTP | 各网段→Ubuntu | **80** | 通 | 完整页面 | **✅ 放行** |

- **结论**：拦截既非按方向、也非按是否加密，而是**按目标端口白名单**。方案 B（TLS）就此被证伪——加密的 ClientHello 在非标准端口上照样被掐。**只要服务走 80 端口即可绕过。**

---

## 四、最终采用方案：Ubuntu 本机 Nginx 80 端口入口

### 原理
中间设备按目标端口白名单拦截，**80 端口放行**。因此让 Ubuntu 自己用 Nginx 监听 80 对外，前端静态文件与后端 `/api` 全部同源经 80 出去，即可绕过拦截。无需 Windows 中转、无需隧道、无需 TLS。前后端同源后 **CORS 问题自然消失**。

### 架构

```text
各网段用户浏览器
  → http://<Ubuntu-IP>/        (80 端口，中间设备放行)
  → Ubuntu Nginx
      ├─ /              → 前端静态页面 (frontend/dist)
      └─ /api/、/api/ws → 反代 127.0.0.1:8080 (Spring Boot 后端)
```

### 相关文件（已随项目提供）
- `deploy/nginx/material-pull.conf` — 原生 Nginx 配置（监听 80，反代 `127.0.0.1:8080`，`server_name _` 接受任意 IP/主机名）。
- `deploy/scripts/setup-nginx-gateway.sh` — 一键安装脚本（装 Nginx、部署配置、放行 dist 目录读取权限、测试并重载）。

### 部署步骤

```bash
# 1. 构建前端静态文件
cd /home/tthih/IE/frontend && npm run build
# 2. 启动后端（监听 127.0.0.1:8080）
bash /home/tthih/IE/deploy/scripts/start.sh backend
# 3. 安装并启用 Nginx 80 入口（需 root）
sudo bash /home/tthih/IE/deploy/scripts/setup-nginx-gateway.sh
```

### 验证

```bash
# 本机
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/            # 首页 200
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/api/health/ready  # 反代 200
```

各网段用户浏览器访问 `http://<Ubuntu-IP>/`，用 `admin` / 见 env 的初始密码登录即可。

---

## 五、抗 IP 变化（换位置插网线）

Ubuntu 无固定 IP。得益于本方案：

- **服务端零改动**：Nginx `server_name _` 接受任意地址，反代目标是本机回环 `127.0.0.1:8080`，与外部 IP 无关。换 IP 后 Nginx / 后端都不用改。
- **地址自动播报**：`deploy/scripts/announce-access-url.sh` + `material-pull-url-announcer.service` 开机自启，检测到 IP 变化时（15 秒内）把最新访问地址写到家目录 `物料拉动系统-访问地址.txt`。换位置后打开该文件即可看到新地址发给同事。

安装播报服务（需 root，可选）：

```bash
sudo cp /home/tthih/IE/deploy/systemd/material-pull-url-announcer.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now material-pull-url-announcer.service
cat ~/物料拉动系统-访问地址.txt
```

> mDNS（`主机名.local`）已实测**跨网段不可用**（组播默认不跨三层网段 + 中间设备不转发），故不采用。若日后需要稳定域名，最优解是找 IT 在内网 DNS 分配固定域名。

---

## 六、备用方案（当前未使用，留档）

若日后 80 端口也被封，或需经 Windows 统一入口，可考虑：
- **方案 A**：向 IT 申请对指定会话有状态放行（见第八节模板，根本解法）。
- **反向隧道（FRP / SSH -R）**：Ubuntu 主动出站到 Windows。注意本次实测 Ubuntu→Windows 出站在非标准端口（8080）同样被 RST，且 Windows SSH 22 默认未监听——采用前需先验证隧道所用端口未被拦、并取得 IT 授权。

---

## 七、部署原则与实施清单

### 为什么项目本体放 Ubuntu，而非 Windows Server
直接把 Spring Boot + Vue + SQL 部署到 Windows Server 技术上可行且无需重启，但不推荐：
1. 端口冲突（80/1433/3306/6688/8080/9090 已占用）。
2. 安装 Java 可能改动 PATH/注册表/环境变量，与现有 Java/Tomcat 冲突。
3. 与生产业务争抢 CPU/内存，异常时可能拖垮整机。
4. 动现有数据库实例风险高。
5. **部署在 Windows 也绕不开中间设备 RST 注入**，其它网段用户访问仍可能被拦。

结论：项目本体（应用 + 数据库 + 依赖）全部部署在 Ubuntu，Windows 只做入口。

### 实施前检查清单
- [ ] 前端已构建：`frontend/dist/index.html` 存在。
- [ ] 后端在 `127.0.0.1:8080` 正常（`curl http://127.0.0.1:8080/api/health/ready` 返回 200）。
- [ ] 80 端口未被其他进程占用（Nginx 需绑定）。
- [ ] Nginx `dist` 目录读取权限已放行（安装脚本已处理家目录执行位）。
- [ ] 地址播报服务已启用，`~/物料拉动系统-访问地址.txt` 能生成。

### 实施顺序
1. 在 Ubuntu 部署 Spring Boot + Vue + 数据库，本地 `127.0.0.1:8080` 跑通。
2. `npm run build` 构建前端静态文件。
3. `sudo bash deploy/scripts/setup-nginx-gateway.sh` 安装 Nginx 80 入口。
4. 本机验证首页与 `/api` 反代均 200，再从其他网段浏览器访问 `http://<Ubuntu-IP>/`。
5. 完整测试：首页、静态资源、登录、上传下载、WebSocket、大文件、超时、重启恢复。

---

## 八、给 IT / 网络管理员的放行申请模板（方案 A）

```text
申请用途：
Windows Server 需将内部 Web 请求反向代理到 Ubuntu 主机。

现象：
10.243.111.152 → 10.243.129.77 的 TCP 目标端口，
TCP 握手与 HTTP 请求均可通过，
但服务器返回 HTTP 响应时，会话被一台 TTL=64 的设备注入 RST 掐断。
抓包证明该 RST 非两端主机发出（两端真实包 TTL=127，RST 包 TTL=63）。

申请：
对 源 10.243.111.152 → 目标 10.243.129.77 指定 TCP 端口
做有状态放行，不做应用层阻断（不注入 RST）。

无需开放：
SSH 22 / Ubuntu 对其他网段的访问 / 整个网段。

对外入口：
仍由 Windows Server 的 HTTPS 443 提供。
```

---

## 九、安全边界与回退

最终方案全部改动都在 Ubuntu 本机，未修改 Windows Server、IIS、SUNPN PC SERVER、公司网络设备或安全软件配置。Windows 侧唯一遗留的是排查期加的临时 /32 路由（非持久，重启即失效）。

Ubuntu 侧新增内容与回退方式：
- **Nginx（80 入口）**：`sudo systemctl disable --now nginx` 停用；`sudo apt remove --purge nginx nginx-common` 卸载。配置在 `/etc/nginx/sites-enabled/material-pull.conf`。
- **地址播报服务**：`sudo systemctl disable --now material-pull-url-announcer.service` 停用；删除 `/etc/systemd/system/material-pull-url-announcer.service` 即可。
- **后端**：`bash deploy/scripts/stop.sh backend`。

Windows 临时路由回退（如需）：`route delete 10.242.36.66`。

### 安全提示
- 当前对外入口为 **HTTP 明文 80**，内网可用；如需加密，可在 80 之外另配 443（`frontend/nginx-tls.conf` 提供了模板，证书用 `deploy/scripts/gen-self-signed-cert.sh` 自签），但注意中间设备对 443 的放行需另行验证。
- 后端 8080 仅监听本机、经 Nginx 反代，未直接对外。
- `/api/actuator/` 已在 Nginx 层限制为内网网段可访问。

