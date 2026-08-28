# DataScope 实施改造清单

> 本文件作为 Codex / 开发团队执行主线。原则：先账号和认证基础，再业务封堵；不能先改前端筛选宣称完成隔离。

## 阶段 0：重新核验与决策冻结

- [ ] 核对当前 Git HEAD、工作区未提交改动。
- [ ] 核对实际 MySQL 实例、profile、连接配置。
- [ ] 核对生产表结构与 Migration 执行状态。
- [ ] 重新统计 NULL、空串、非法 factory/area。
- [ ] 创建并验证本次生产备份。
- [ ] 完成 `03_业务归属与权限决策.md` 的编码前决策。

## 阶段 1：用户范围数据库和管理

- [ ] `sys_user` 增加 `factory`。
- [ ] 新建 `sys_user_delivery_area(user_id, delivery_area)`。
- [ ] 建立 `UNIQUE(user_id, delivery_area)`。
- [ ] 增加用户范围 Entity / Repository。
- [ ] 用户创建/修改时：普通账号必须有一个工厂和至少一个区域；ADMIN 可为空表示全局。
- [ ] `BasicDataDtos.UserRequest/UserResponse` 增加 factory/deliveryAreas。
- [ ] `Users.vue` 增加工厂选择、多区域分配。
- [ ] 用户 Role/factory/areas 变化时立即失效旧 Session/Ticket。

## 阶段 2：认证链和 DataScope 基础设施

- [ ] `AuthDtos.LoginResult` 增加 factory/deliveryAreas。
- [ ] `SessionUser` 增加范围字段。
- [ ] 内存 SessionStore、Redis SessionStore 同步。
- [ ] WebSocket Ticket 携带范围。
- [ ] `AuthTokenFilter` 将范围绑定至 `RequestContext`。
- [ ] 请求结束清理 ThreadLocal。
- [ ] `RequestContext` 增加工厂/区域读取方法。
- [ ] 新建统一 `DataScopeService`。
- [ ] 普通账号范围缺失 fail closed。
- [ ] 旧 Session 缺少新字段时强制重新登录。
- [ ] 配置中不提供生产可关闭 DataScope 的旁路开关。

建议服务能力：

```text
isGlobal()
currentFactory()
currentDeliveryAreas()
canAccess(factory, deliveryArea)
requireAccess(factory, deliveryArea)
```

## 阶段 3：Task 封堵

Repository：

- [ ] 列表按 `factory + deliveryArea IN (...)` 查询。
- [ ] 状态、日期、统计加入范围条件。
- [ ] 待打印任务加入范围条件。
- [ ] taskNo 加锁后立即 `requireAccess()`，或锁查询直接限定范围。
- [ ] 重复任务判断加入业务所需范围维度。

Service：

- [ ] 取消、接单、拣料、配送、收货、异常、重试、强制完成全部在变更前校验旧实体归属。
- [ ] 普通账号不得通过请求参数修改 Task factory/area。
- [ ] 扫码创建只接受后端唯一解析出的 Mapping/Label 范围。

ScanService：

- [ ] 删除 deliveryArea 缺失时默认 `1`。
- [ ] 删除多候选 `get(0)`/取第一条逻辑。
- [ ] factory/area 任一为空拒绝创建。

## 阶段 4：Mapping 与导入

- [ ] 列表、分页、详情、导出按 DataScope。
- [ ] `deleteByDeliveryArea` 改为 factory + deliveryArea。
- [ ] `scope=ALL` 仅全局 ADMIN。
- [ ] IDs 删除逐项/SQL 范围校验，包含越权项整体拒绝。
- [ ] 普通账号保存时后端强制为账号范围。
- [ ] upsert 的唯一性判断加入必要范围维度。
- [ ] 冲突检测只在同工厂/同区域内判断。
- [ ] 导入不能通过文件内容或前端参数扩大账号权限。

导入规则：

```text
普通账号：后端强制覆盖 factory/area 为账号可信范围
管理员：必须明确指定或从文件解析，并校验合法性
激活/覆盖：必须验证操作者拥有目标范围
```

## 阶段 5：PrintJob 与机器身份

- [ ] PrintJob 列表、创建、补打、取消按范围。
- [ ] 从 Task 创建前校验 Task。
- [ ] 人工入口通过 taskNo 校验范围。
- [ ] Agent 领取只能领取绑定 factory 的作业。
- [ ] 回调通过 printJobNo 反查实体，再校验 Agent factory。
- [ ] 决定是否增加 deliveryArea 快照。

Print Agent 目标：

- [ ] `sys_print_agent`。
- [ ] `X-Agent-Id`。
- [ ] `X-Agent-Credential`。
- [ ] BCrypt/等价安全哈希保存凭据。
- [ ] Agent enabled/lastSeen。
- [ ] 凭据轮换与审计。

## 阶段 6：Inventory / Label / Box / AGV

按照已审批决策执行：

- [ ] Inventory 增加/回填范围，列表和统计 SQL 范围化。
- [ ] Label 独立查询、配对、追溯按范围。
- [ ] Box 候选、箱池、状态列表按范围。
- [ ] 明确 areaCode 与 deliveryArea 的关系。
- [ ] AGV 派单前通过 Task/自身快照校验范围。
- [ ] AGV 回调使用可信机器身份和作业号反查，不信任请求体归属。

## 阶段 7：计划、BOM、需求、采购

WeeklyPlan：

- [ ] 查询按范围。
- [ ] 导入时校验/覆盖 factory。
- [ ] 激活批次校验范围。
- [ ] 冲突检测加入范围。

SimpleBOM：

- [ ] 按决策增加 factory（若工厂隔离）。
- [ ] 每个工厂独立活动批次。
- [ ] 500 万行流式导入保持性能，不用逐行额外 N+1 查询。

ProductionPlan / MaterialDemand / PurchaseRequirement：

- [ ] 按已确认业务模型增加或继承范围。
- [ ] 计算、删除、覆盖、唯一业务键加入必要范围维度。
- [ ] BOM、库存、工位物料、Mapping 使用相同范围。

## 阶段 8：Dashboard、统计、日志、导出、维护

> 本阶段优先级高于 WebSocket，因为默认 Dashboard 会主动展示统计结果。

Dashboard：

- [ ] 删除普通请求 `inventoryRepository.findAll()` 后内存过滤。
- [ ] Material/Box/Label count 改为范围统计。
- [ ] Task 状态图、最新任务按范围 SQL。
- [ ] ScanLog 最新记录按范围。
- [ ] ADMIN 可提供明确全局或工厂/区域筛选。

其他：

- [ ] MaterialUsageDashboardService 范围化。
- [ ] BigScreen 范围化。
- [ ] LogExportService 列表、详情、导出范围化。
- [ ] 告警带范围。
- [ ] Recovery/DataQuality 普通入口不得泄露全局数据。
- [ ] 全局后台任务允许明确内部遍历，但每条日志/告警/推送保留原范围。
- [ ] 普通管理员清空只作用自身范围；全局清空独立入口、二次确认、审计。

## 阶段 9：WebSocket / Redis

- [ ] Ticket 范围进入连接上下文。
- [ ] 创建可信 Principal。
- [ ] STOMP SUBSCRIBE 拦截器。
- [ ] destination 白名单。
- [ ] 事件增加 factory/deliveryArea。
- [ ] 无归属事件仅全局管理员可接收。
- [ ] 使用 `/user/.../queue/...` 或等价定向推送。
- [ ] Redis relay 保留范围元数据。
- [ ] 每个实例按当前连接权限再次过滤。
- [ ] 禁止公共 `/topic/*` 无条件广播完整业务实体。

## 阶段 10：前端

- [ ] `auth.ts` 保存 factory/deliveryAreas。
- [ ] App 显示当前范围。
- [ ] Users 维护范围。
- [ ] WarehouseTasks 不再下载全量后过滤。
- [ ] FactoryPrint 只基于后端已授权的完整待打印集合生成区域，不能先分页再形成区域列表。
- [ ] Mappings、Dashboard、BigScreen、WeeklyPlan、SimpleBom、Planning、Inventory、BoxPool、BoxStatus、AgvJobs、Labels、Logs、Integrations、Maintenance、FactoryHealth 全部使用后端隔离结果。
- [ ] `api.ts` 统一 403/404、导出错误处理。
- [ ] 前端请求范围参数只能进一步收窄，不能扩大权限。

## 阶段 11：历史数据

- [ ] 重新统计 Task/PrintJob 未归属数据。
- [ ] 生成 Task 唯一候选。
- [ ] 多候选、无候选进入人工清单。
- [ ] 关键业务字段一致后才允许审批。
- [ ] Task 审批后再生成 PrintJob 候选。
- [ ] 无 Task 的 PrintJob 单独处理。
- [ ] 验证环境演练。
- [ ] 生产分批回填。
- [ ] 未确认记录保持未归属，只对 ADMIN 可见。

## 阶段 12：测试与上线

详见 `06_测试验收发布与回滚.md`。
