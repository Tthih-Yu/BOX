-- BOM 周计划联动 · 第一步(表结构) + 第二步(高速导入) 用表：简单 BOM + 批次切换。
-- 设计：每行 BOM 归属一个 batch_no；同一时刻只有一个批次为 ACTIVE，业务查询只认活跃批次。
-- 新导入先建为 RUNNING/READY 批次，校验通过后“激活切换”为 ACTIVE，旧活跃批次转 ARCHIVED 保留可回滚。
-- 生产 MySQL 使用 ddl-auto=validate，必须先执行本 SQL 再启动新版后端。执行前请先备份。

-- 批次表：一次 BOM 导入即一个批次，记录行数统计、状态与操作人。
CREATE TABLE IF NOT EXISTS t_simple_bom_batch (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    batch_no      VARCHAR(64)  NOT NULL COMMENT '批次号，导入时生成',
    file_name     VARCHAR(255)          COMMENT '导入文件名',
    status        VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/READY/ACTIVE/ARCHIVED/FAILED',
    total_rows    INT          NOT NULL DEFAULT 0 COMMENT '解析总行数',
    success_rows  INT          NOT NULL DEFAULT 0 COMMENT '成功入库行数',
    failed_rows   INT          NOT NULL DEFAULT 0 COMMENT '失败行数',
    operator      VARCHAR(64)           COMMENT '操作人',
    remark        VARCHAR(500)          COMMENT '备注',
    started_at    DATETIME              COMMENT '导入开始时间',
    finished_at   DATETIME              COMMENT '导入结束时间',
    activated_at  DATETIME              COMMENT '激活为当前有效批次的时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bom_batch_no (batch_no),
    KEY idx_bom_batch_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简单BOM导入批次';

-- BOM 明细表：仅物料8D号 → 组件8D号；不含用量、不参与数量计算。数据量大(500万级)。
CREATE TABLE IF NOT EXISTS t_simple_bom (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    batch_no       VARCHAR(64) NOT NULL COMMENT '所属导入批次',
    material_code  VARCHAR(32) NOT NULL COMMENT '物料8D号(8位纯数字)',
    component_code VARCHAR(32) NOT NULL COMMENT '组件8D号(8位纯数字)，即料号映射 lineMaterialCode',
    created_at     DATETIME             COMMENT '写入时间',
    PRIMARY KEY (id),
    KEY idx_sbom_batch (batch_no),
    KEY idx_sbom_batch_material (batch_no, material_code),
    KEY idx_sbom_batch_component (batch_no, component_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简单BOM(物料->组件)';
