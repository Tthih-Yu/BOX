# 物料拉动系统 Factory Ready V0.8.4 生产落地版

本版在原有真实工厂标签、扫码拉动、任务流转、库存、导入、AGV/外部接口基础上，重点重写了鉴权、角色权限、密码、库存一致性、幂等、导入校验和部署配置。源码中不再保留编译出来的 `.class` 文件。

## 一、主要改动

- 后端新增角色权限拦截：管理员、计划员、仓库员、产线员工、只读用户、系统接口分别控制接口访问。
- 用户密码改为 BCrypt Hash 存储，用户列表不再返回密码字段，修改密码通过“新密码”字段单独提交。
- H2 Console、Swagger 默认关闭，仅 `dev` profile 开启。
- WebSocket 不再默认把长期 token 放 URL，改为 `/auth/ws-ticket` 短期一次性票据。
- 外部系统回调改为 `X-Api-Key` 机器认证，可通过 `MATERIAL_PULL_EXTERNAL_API_KEY` 环境变量配置。
- 库存保存改为服务层校验，锁定数量和可用库存不再由前端直接修改。
- 任务强制完成不再吞掉库存扣减失败；库存不足时不会悄悄把库存扣成 0。
- 标签实体不再把普通条码自动污染到 `warehouseCode`，并增加关键扫码字段唯一约束。
- Excel/CSV 导入不再把非法数字静默转为 0，错误会写入导入错误明细。
- 前端默认开发服务只监听 `127.0.0.1`；需要局域网访问时使用 `npm run dev:lan`。
- systemd 部署改为固定启动 `/opt/material-pull-system/backend/app.jar`，避免版本号变更导致启动失败。
- 移除内置初始化业务数据和本地假成功接口，打印与 AGV 必须配置真实服务地址；未配置时请求会失败并返回明确错误。
- 打印任务、AGV任务增加真实接口下发、取消和回调状态保存；满箱配送 AGV 与空箱回收 AGV 分开记录，避免互相覆盖。
- 物料、标签、补货任务增加物料图片字段，标签模板可携带图片地址。
- 基础数据删除改为停用，不再物理删除历史业务引用。

## 二、技术栈

后端：Java 17、Spring Boot 3.3.x、Spring Web、Spring Data JPA、Validation、WebSocket / STOMP、H2 文件数据库、MySQL 驱动、Springdoc Swagger UI、Apache POI、Apache Commons CSV、Spring Security Crypto。

前端：Vue 3、TypeScript、Vite、Element Plus、Axios、Vue Router。

## 三、目录结构

```text
material-pull-system
├─ backend                         后端工程
│  ├─ src/main/java/com/example/materialpull
│  │  ├─ common                    通用返回、异常、请求上下文、安全配置、摘要工具
│  │  ├─ security                  角色注解与权限拦截器
│  │  ├─ config                    跨域、WebSocket、首次管理员初始化
│  │  ├─ controller                REST API
│  │  ├─ dto                       请求/响应对象
│  │  ├─ entity                    数据实体
│  │  ├─ enums                     业务枚举
│  │  ├─ repository                JPA仓库
│  │  ├─ resilience                幂等、业务锁、操作保护
│  │  ├─ maintenance               健康检查与恢复
│  │  └─ service                   业务服务
├─ frontend                        Vue3 Web 管理端
├─ deploy                          nginx / systemd 部署文件
└─ docs                            SQL升级脚本、导入模板、现场标签图片资源
```

## 四、启动方式

开发环境后端：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

开发环境前端：

```bash
cd frontend
npm install
npm run dev
```

默认地址：

```text
前端：http://localhost:5173
后端：http://localhost:8080/api
```

`dev` profile 下才开启：

```text
Swagger：http://localhost:8080/api/swagger-ui.html
H2控制台：http://localhost:8080/api/h2-console
```

## 五、首次管理员

系统不再内置初始化账号。首次部署请通过环境变量 `MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD` 创建初始管理员，密码长度不得少于 10 位且必须同时包含字母和数字；创建成功并登录后建议删除该环境变量，后续用户统一在“系统管理 → 用户角色”维护。

## 六、外部接口认证

AGV 回调、SAP/IMS/PPC 接口可使用机器密钥：

```bash
export MATERIAL_PULL_EXTERNAL_API_KEY='替换为高强度随机字符串'
```

请求头：

```text
X-Api-Key: 替换为高强度随机字符串
```

未配置该环境变量时，外部系统机器认证不会通过，仍可由管理员在后台手工调试。


## 七、生产外设接口配置

生产环境不会使用本地假成功逻辑。打印与 AGV 未配置真实地址时，系统会拒绝下发，避免现场误判为已经执行。

```bash
export MATERIAL_PULL_DEFAULT_PRINTER='真实标签打印机名称'
export MATERIAL_PULL_PRINT_SUBMIT_URL='https://print-gateway.example.com/api/print/submit'
export MATERIAL_PULL_PRINT_CANCEL_URL='https://print-gateway.example.com/api/print/cancel'
export MATERIAL_PULL_PRINT_API_KEY='替换为打印服务密钥'

export MATERIAL_PULL_AGV_DISPATCH_URL='https://agv.example.com/api/jobs'
export MATERIAL_PULL_AGV_CANCEL_URL='https://agv.example.com/api/jobs/cancel'
export MATERIAL_PULL_AGV_API_KEY='替换为AGV服务密钥'
```

打印服务状态回调地址：

```text
POST /api/print-jobs/callback
Header: X-Api-Key: MATERIAL_PULL_EXTERNAL_API_KEY
```

AGV 状态回调地址：

```text
POST /api/agv-jobs/callback
Header: X-Api-Key: MATERIAL_PULL_EXTERNAL_API_KEY
```

MySQL 生产库建议先执行：

```text
docs/sql/V0.8.4__production_upgrade.sql
```

## 八、生产部署提醒

- 前端必须使用 `npm run build` 后的静态文件，不要用 Vite dev server 当生产服务。
- 后端生产建议使用 MySQL profile，并通过环境变量配置数据库账号密码。
- systemd 文件默认启动 `/opt/material-pull-system/backend/app.jar`，部署时把真实 jar 建软链接到 `app.jar`。
- 不要在生产开启 `dev` profile。
