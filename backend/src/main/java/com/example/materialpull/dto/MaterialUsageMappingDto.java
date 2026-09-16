package com.example.materialpull.dto;

/** 看板物料映射轻量视图，只保留筛选、对照和表格展示所需字段。 */
public record MaterialUsageMappingDto(
        String lineMaterialCode,
        String warehouseCode,
        String warehouseMaterialCode,
        String factory,
        String stationCode,
        String warehouseLocation,
        String deliveryAddress,
        String deliveryArea
) {}
