-- =====================================================
-- DataScope Migration V0.9.7
-- 功能：业务对象范围字段 - Production Plan 链
-- 作者：AI Codex
-- 日期：2026-08-31
-- 说明：Expand Schema，向后兼容
-- =====================================================

-- 1. ProductionPlan 增加 factory 字段
ALTER TABLE t_production_plan
ADD COLUMN factory VARCHAR(32) NULL COMMENT '所属工厂（按产线所属工厂）';

-- 2. MaterialDemand 增加 factory 字段
ALTER TABLE t_material_demand
ADD COLUMN factory VARCHAR(32) NULL COMMENT '所属工厂快照（继承自计划）';

-- 3. PurchaseRequirement 增加 factory 字段
ALTER TABLE t_purchase_requirement
ADD COLUMN factory VARCHAR(32) NULL COMMENT '所属工厂快照（继承自需求）';

-- 4. 增加索引
CREATE INDEX idx_plan_factory ON t_production_plan(factory);
CREATE INDEX idx_demand_factory ON t_material_demand(factory);
CREATE INDEX idx_purchase_factory ON t_purchase_requirement(factory);

-- =====================================================
-- 说明：
-- 1. 计划链按 FACTORY_ONLY 模式（不需要 deliveryArea）
-- 2. 下游对象从上游继承并固化 factory
-- 3. 保持计划链范围一致性
-- =====================================================
