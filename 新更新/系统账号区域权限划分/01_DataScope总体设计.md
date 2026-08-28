# DataScope 总体设计

> 本文件只描述目标设计和稳定原则，不记录容易随代码变化的实现现状。

## 1. 设计目标

系统不拆库、不复制业务服务。通过账号范围控制同一套业务数据的可见与可操作范围。

普通账号必须绑定：

- 一个非空 `factory`；
- 一个或多个 `deliveryArea`。

普通账号访问业务数据必须同时满足：

```text
data.factory == currentUser.factory
AND
currentUser.deliveryAreas contains data.deliveryArea
```

`ADMIN` 明确拥有全局能力。未归属或无法可靠确定归属的历史数据只允许全局管理员访问。

## 2. 三层权限边界

### 2.1 Role

决定账号是否具备某类功能，例如查询、接单、删除、导入、打印、维护。

### 2.2 MenuPermission

建议定义为前端菜单/入口可见性控制：

- 可隐藏不需要的菜单；
- 可以改善不同账号的操作界面；
- 不应成为后端 API 的最终授权依据；
- 不应通过菜单配置扩大 Role 或 DataScope。

如果后续确认 MenuPermission 具备账号级功能授权能力，应明确它与 Role 是“收窄关系”，不能绕开 Role。

### 2.3 DataScope

决定业务数据范围：工厂 + 配送区域。

最终后端授权必须至少同时满足：

```text
Role 允许操作
AND
DataScope 允许访问目标实体
```

## 3. DataScopeService

统一组件至少提供：

```text
isGlobal()
currentFactory()
currentDeliveryAreas()
canAccess(factory, deliveryArea)
requireAccess(factory, deliveryArea)
```

原则：

- Service 层是最终安全边界。
- Repository 应尽量在 SQL 阶段限制范围。
- Controller 负责角色和入口级参数校验，但不能替代 Service 的范围校验。
- 按 `id`、`taskNo`、`printJobNo`、`labelCode` 等找到实体后仍必须检查实体归属。
- 批量操作包含任何越权项时整体拒绝，不静默跳过。
- 普通账号不得修改实体归属。
- 普通账号创建数据时由后端强制赋予可信范围，不信任客户端提交的扩大范围参数。
- 范围缺失时 fail closed，不使用默认工厂或区域。

## 4. 账号与认证链

目标数据库模型：

```text
sys_user.factory                     单工厂
sys_user_delivery_area               多区域关联
UNIQUE(user_id, delivery_area)
```

需要同步进入：

```text
UserEntity / User DTO
        ↓
LoginResult
        ↓
SessionUser（内存/Redis）
        ↓
WebSocket Ticket
        ↓
AuthTokenFilter
        ↓
RequestContext
        ↓
DataScopeService
```

角色、工厂、区域集合变化后必须立即失效旧 Session 和旧 WebSocket Ticket。旧 Session 缺少范围字段时强制重新登录，不兼容为全局权限。

## 5. 异常与审计原则

建议统一策略：

- 列表查询：只返回授权范围，不因存在其他范围数据而报错。
- 明确资源越权：同类接口统一采用 403 或 404 策略。
- 防止通过编号探测资源存在性时，可统一返回 404。
- 批量越权：整体拒绝。

审计至少记录：

- 用户 ID、用户名、Role；
- factory、deliveryAreas；
- 资源类型、资源标识；
- 动作；
- 成功/失败/越权；
- 时间、TraceId。

不得记录 Token、API Key、机器凭据或原始密码。

## 6. 扫码链设计边界

Android、微信小程序、阿里云、WinServer、Linux Worker 的扫码协议保持现有结构，不要求客户端增加后台账号 DataScope 字段。

统一原则：

```text
扫码请求
   ↓
ScanService
   ↓
Label / Mapping 解析唯一 factory + deliveryArea
   ↓
创建 Task 并固化归属
```

必须满足：

- 不信任客户端提交的 factory 或区域决定业务归属；
- 不使用默认区域 `1`；
- 不在多 Mapping 候选时取第一条；
- 工厂或区域为空、多候选、无候选均拒绝创建；
- employeeNo 仅作为操作人/审计字段，不作为后台 DataScope。

## 7. WebSocket 与 Redis

HTTP 完成隔离后，实时推送必须同步隔离。

目标：

1. Ticket 携带用户、Role、factory、deliveryAreas。
2. CONNECT 后建立可信 Principal/Session 范围。
3. STOMP `SUBSCRIBE` 通过服务端拦截器校验 destination。
4. 事件包含明确 `factory`、`deliveryArea` 元数据。
5. 普通账号只接收同工厂、授权区域事件。
6. Redis relay 传播事件时保留范围元数据，每个实例再次按连接权限过滤。
7. 不向公共业务 `/topic/*` 无条件广播完整业务实体。
8. 优先采用用户队列/定向推送；实现时验证 Spring `convertAndSendToUser` 的 Principal 路由行为。

## 8. 机器主体身份

扫码设备可以保持既有采集协议，但会读取、领取或回调既有业务对象的机器主体必须有可信身份边界：

- Print Agent；
- AGV；
- SAP；
- IMS；
- PPC。

不能仅因为身份为 `SYSTEM` 就默认具有全库权限。

Print Agent 推荐：

```text
X-Agent-Id
X-Agent-Credential
        ↓
sys_print_agent
        ↓
绑定 factory
        ↓
领取/回调仅允许该工厂 PrintJob
```

请求体 factory 只能进一步收窄筛选，不能扩大 Agent 权限。

## 9. 前端原则

前端负责展示、减少误操作，不承担安全边界。

- 只消费后端已经隔离的数据。
- 不再下载全量数据后按工厂/区域过滤。
- 管理员筛选参数仍需后端校验。
- `Users.vue` 用于维护账号 factory 与 deliveryAreas。
- `auth.ts` 保存后端返回范围。
- 403/404 和导出在 `api.ts` 统一处理。
