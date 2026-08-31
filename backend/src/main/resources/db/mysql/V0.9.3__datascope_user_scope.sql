-- =====================================================
-- DataScope Migration V0.9.3
-- 功能：账号范围基础结构
-- 作者：AI Codex
-- 日期：2026-08-31
-- 说明：Expand Schema，向后兼容，旧代码仍可运行
-- =====================================================

-- 1. sys_user 增加 factory 字段（可空）
ALTER TABLE sys_user 
ADD COLUMN factory VARCHAR(32) NULL COMMENT '所属工厂，NULL 表示未设置或全局管理员';

-- 2. 创建用户配送区域关联表
CREATE TABLE IF NOT EXISTS sys_user_delivery_area (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    delivery_area VARCHAR(64) NOT NULL COMMENT '配送区域代码',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_area (user_id, delivery_area),
    INDEX idx_user_id (user_id),
    INDEX idx_delivery_area (delivery_area)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户配送区域关联表';

-- 3. 创建工厂字典表
CREATE TABLE IF NOT EXISTS sys_factory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    factory_code VARCHAR(32) NOT NULL UNIQUE COMMENT '工厂代码',
    factory_name VARCHAR(128) COMMENT '工厂名称',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工厂字典表';

-- 4. 创建配送区域字典表
CREATE TABLE IF NOT EXISTS sys_delivery_area (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    factory_code VARCHAR(32) NOT NULL COMMENT '所属工厂',
    area_code VARCHAR(64) NOT NULL COMMENT '区域代码',
    area_name VARCHAR(128) COMMENT '区域名称',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_factory_area (factory_code, area_code),
    INDEX idx_factory (factory_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送区域字典表';

-- 5. 初始化工厂数据
INSERT INTO sys_factory (factory_code, factory_name, enabled, display_order) VALUES
('弋江', '弋江工厂', TRUE, 1);
-- 三山工厂待后续添加

-- 6. 初始化配送区域数据（基于用户提供的 17 个区域）
INSERT INTO sys_delivery_area (factory_code, area_code, area_name, enabled, display_order) VALUES
('弋江', 'T26 Floor', 'T26 Floor', TRUE, 1),
('弋江', 'T18FL4-2 Floor', 'T18FL4-2 Floor', TRUE, 2),
('弋江', 'T18FL4 Floor', 'T18FL4 Floor', TRUE, 3),
('弋江', '燃油右舵', '燃油右舵', TRUE, 4),
('弋江', '高压发动机区域', '高压发动机区域', TRUE, 5),
('弋江', 'T1GC Floor', 'T1GC Floor', TRUE, 6),
('弋江', 'EOVA Floor', 'EOVA Floor', TRUE, 7),
('弋江', 'EHYFL1 Floor', 'EHYFL1 Floor', TRUE, 8),
('弋江', 'EOY Floor', 'EOY Floor', TRUE, 9),
('弋江', 'T2Y Floor', 'T2Y Floor', TRUE, 10),
('弋江', 'LP&H19小房间', 'LP&H19小房间', TRUE, 11),
('弋江', 'T18FL3 Floor', 'T18FL3 Floor', TRUE, 12),
('弋江', 'T26 IP', 'T26 IP', TRUE, 13),
('弋江', 'T1GC IP', 'T1GC IP', TRUE, 14),
('弋江', 'EOVA IP', 'EOVA IP', TRUE, 15),
('弋江', 'T18FL4 IP', 'T18FL4 IP', TRUE, 16),
('弋江', 'EHYFL1 IP', 'EHYFL1 IP', TRUE, 17);

-- 7. sys_user 增加 version 字段（乐观锁）
ALTER TABLE sys_user 
ADD COLUMN version BIGINT DEFAULT 0 COMMENT '乐观锁版本号';

-- =====================================================
-- 向后兼容性说明：
-- 1. sys_user.factory 为 NULL - 旧代码不受影响
-- 2. sys_user_delivery_area 为空 - 旧代码不受影响
-- 3. 字典表初始化 - 不影响现有功能
-- 
-- 新代码可以开始使用这些字段，但在安全切换前：
-- - 登录仍返回 NULL 范围
-- - 业务逻辑不强制校验范围
-- =====================================================
