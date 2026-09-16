package com.example.materialpull.dto;

import com.example.materialpull.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 看板任务轻量视图，避免向浏览器传输完整补货任务实体。 */
public record MaterialUsageTaskDto(
        String factory,
        String stationCode,
        String stationName,
        String sendStationAddress,
        String deliveryAddress,
        String materialCode,
        String sourceLabelCode,
        String deliveryArea,
        TaskStatus status,
        BigDecimal requestQty,
        String requestUnit,
        String warehouseCode,
        String warehouseMaterialCode,
        String warehouseAddress,
        String warehouseLocation,
        LocalDateTime createdAt
) {}
