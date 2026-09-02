-- 增加三山工厂及现有业务区域；幂等执行，仅写入范围字典。
START TRANSACTION;
INSERT INTO sys_factory (factory_code, factory_name, enabled, display_order)
VALUES ('三山', '三山工厂', TRUE, 2)
ON DUPLICATE KEY UPDATE factory_name=VALUES(factory_name), enabled=TRUE;

INSERT INTO sys_delivery_area (factory_code, area_code, area_name, enabled, display_order) VALUES
 ('三山','SS-LP','SS-LP',TRUE,1),
 ('三山','SS-M32T FL3-IP','SS-M32T FL3-IP',TRUE,2),
 ('三山','SS-LP-自动盲栓区','SS-LP-自动盲栓区',TRUE,3)
ON DUPLICATE KEY UPDATE area_name=VALUES(area_name), enabled=TRUE;
COMMIT;
