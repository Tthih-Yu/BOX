# DataScope 改造文档索引

> 状态：设计整合版 / 编码前决策阶段  
> 整理日期：2026-08-28  
> 基准提交：`71abc038ece544cb286d38241297da027db0a1a6`  
> 适用项目：`/opt/apps/material-pull`  

## 1. 改造目标

系统继续使用同一套前端、后端和数据库，通过“功能权限 + 数据范围权限”共同授权。

普通账号：

```text
factory = 1 个工厂
deliveryAreas = 1 个或多个配送区域
```

访问规则：

```text
data.factory == user.factory
AND
data.deliveryArea IN user.deliveryAreas
```

`ADMIN` 为明确的全局账号，可访问全部工厂和区域。普通账号范围缺失时必须拒绝访问，不使用默认工厂或默认区域。

## 2. 权限模型

```text
Role             功能权限：能不能执行某类操作
MenuPermission   菜单可见性：前端是否显示入口
DataScope        数据权限：允许访问哪些工厂/区域的数据
```

后端最终安全边界：

```text
Role + DataScope
```

`MenuPermission` 不应单独作为 API 授权依据，也不应通过菜单隐藏代替后端 DataScope。

## 3. 当前核心状态

| 能力 | 当前状态 |
|---|---|
| Role 功能权限 | 已实现 |
| 用户工厂 | 未实现 |
| 用户多区域 | 未实现 |
| LoginResult 范围 | 未实现 |
| SessionUser 范围 | 未实现 |
| RequestContext 范围 | 未实现 |
| DataScopeService | 未实现 |
| Task 范围隔离 | 未实现 |
| Mapping 范围隔离 | 未实现 |
| PrintJob 范围隔离 | 部分实现 |
| 扫码协议兼容 | 可保持现有协议 |
| 扫码任务唯一归属 | 部分实现，需收紧 |
| Dashboard/日志/维护隔离 | 未实现 |
| WebSocket 范围隔离 | 未实现 |
| 外部机器主体范围 | 未实现 |
| 数据库账号范围结构 | 未实现 |
| DataScope 越权测试 | 未实现 |

当前不能仅通过前端选择、隐藏“弋江/三山、配送区域”或给账号命名为“弋江账号/三山账号”来认定数据已经安全隔离。

## 4. 文档导航

1. [`01_DataScope总体设计.md`](01_DataScope总体设计.md)  
   稳定设计原则、账号模型、授权链、WebSocket与机器身份边界。

2. [`02_当前代码核验基线.md`](02_当前代码核验基线.md)  
   只描述当前代码事实。代码变化后优先更新本文件和基准 commit。

3. [`03_业务归属与权限决策.md`](03_业务归属与权限决策.md)  
   Inventory、Box、Label、AGV、PrintJob、计划类对象等必须在编码前完成的业务决策。

4. [`04_DataScope实施改造清单.md`](04_DataScope实施改造清单.md)  
   Codex/开发团队主要执行文档，按阶段列出 Repository、Service、Controller、导入、统计、前端等改造项。

5. [`05_数据库迁移与历史数据.md`](05_数据库迁移与历史数据.md)  
   MySQL/H2 Migration、索引、历史 Task/PrintJob 回填、备份与数据校验。

6. [`06_测试验收发布与回滚.md`](06_测试验收发布与回滚.md)  
   越权、扫码兼容、WebSocket、多实例、性能、发布、灰度和回滚要求。

7. [`07_补充决策与规范.md`](07_补充决策与规范.md) **★ 必读**  
   范围类型定义、账号管理权限矩阵、工厂区域字典、HTTP状态码规范、滚动发布兼容、异步任务处理、审计监控等关键规范。必须在编码前完成所有决策冻结。

## 5. 编码前必须完成的决策

- [ ] 哪些非 `ADMIN` 角色允许一个工厂下绑定多个配送区域。
- [ ] `MenuPermission` 是否仅控制菜单可见性；建议确认其不扩大 API 功能权限或 DataScope。
- [ ] `SimpleBomBatch` 是否按工厂隔离；若隔离，建议增加 `factory`。
- [ ] `ProductionPlan`、`MaterialDemand`、`PurchaseRequirement` 的范围来源和快照策略。
- [ ] `Inventory` 是否直接增加 `factory + deliveryArea`；当前建议为“是”。
- [ ] `Box` 的生命周期与归属规则，明确 `areaCode` 的业务含义。
- [ ] `Label` 是否增加工厂/区域快照；当前建议至少保存工厂快照并验证 Mapping 关联稳定性。
- [ ] `PrintJob` 是否增加 `deliveryArea` 快照。
- [ ] `AGV Job` 是否第一阶段仅通过 Task 校验，后续按查询频率考虑快照。
- [ ] Print Agent 第一阶段采用“独立 Agent 凭据+工厂绑定”，还是暂时保留统一 SYSTEM 认证并在业务层严格校验。
- [ ] SAP、IMS、PPC 等外部系统的独立凭据和范围绑定方式。
- [ ] 历史 Task 候选复核字段、审批人和上线回填流程。

## 6. 统一实施顺序

1. 重新核验运行配置、表结构、当前数据和代码 HEAD，并创建、验证新备份。
2. 完成业务归属和权限决策冻结。
3. 增加用户工厂和多区域数据库结构，管理员为现有账号分配范围。
4. 同步改造 User、DTO、Users.vue、LoginResult、SessionUser、WebSocket Ticket、AuthTokenFilter、RequestContext。
5. 建立统一 `DataScopeService`，完成严格模式、Session 失效和基础测试。
6. 封堵 Task、Mapping、PrintJob 及所有导入、导出、删除、批量操作入口。
7. 改造 Inventory、Label、Box、AGV、WeeklyPlan、SimpleBOM、ProductionPlan、MaterialDemand、PurchaseRequirement。
8. 改造 Print Agent、SAP、IMS、PPC 等机器主体身份和业务范围。
9. 改造 Dashboard、BigScreen、统计、日志、导出、告警、Recovery、DataQuality 等旁路；优先于 WebSocket。
10. 实施 WebSocket/Redis 事件范围、SUBSCRIBE 授权和定向/用户队列隔离。
11. 改造全部前端页面，只消费后端已经隔离的数据。
12. 生成历史 Task/PrintJob 归属候选，审批后回填。
13. 完成越权、扫码兼容、业务回归、WebSocket、多实例和性能测试。
14. 灰度发布、监控、验收。

## 7. 文档维护规则

- `01` 总体设计：目标原则稳定后尽量少改。
- `02` 当前代码核验：每轮代码审查更新，必须记录日期和 commit。
- `03` 业务决策：决策完成后记录“已确认结论、决策人、日期”。
- `04` 实施清单：作为开发任务主线，完成一项勾选一项。
- `05` Migration：禁止把未经验证的生产 SQL 直接写入通用指导文档后自动执行。
- `06` 测试与发布：每项必须有可追溯测试或发布证据。

## 8. 本轮整理说明

原补充报告编号实际为“发现1～7”，但 WebSocket 属于原指导书已经明确记录的核心缺口。本版将其归类为“既有结论再次核验”，其余内容整理为六项新增/深化实施事项，避免误认为 WebSocket 是新发现。
