-- V0.8.5 料号映射新增仓库位置和总装地址字段
-- 来源：新数据库.md 中增加了"仓库位置"和"总装地址"两列
-- 扫码后这两个字段直接透传到仓库标签/补货任务，无需再依赖工位用料数据

ALTER TABLE m_material_mapping
    ADD COLUMN warehouse_location VARCHAR(255) NULL COMMENT '仓库货架位置，如 C-26-D-5',
    ADD COLUMN delivery_address   VARCHAR(255) NULL COMMENT '总装送达地址（工位地址），如 盲栓台-01-A02';
