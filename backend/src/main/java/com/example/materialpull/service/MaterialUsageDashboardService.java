package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.dto.MaterialUsageDashboardDto;
import com.example.materialpull.dto.MaterialUsageMappingDto;
import com.example.materialpull.dto.MaterialUsageTaskDto;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialUsageDashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ReplenishmentTaskRepository taskRepository;
    private final MaterialMappingRepository mappingRepository;
    private final DataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public MaterialUsageDashboardDto dashboard(String from, String to) {
        LocalDate end = parseDate(to, LocalDate.now(BUSINESS_ZONE), "结束日期");
        LocalDate start = parseDate(from, end.minusDays(29), "开始日期");
        if (start.isAfter(end)) throw new BusinessException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        boolean global = dataScopeService.isGlobalAdmin();
        String factory = global ? null : dataScopeService.currentFactory();
        List<String> areas = global ? List.of() : dataScopeService.currentDeliveryAreas();
        List<MaterialUsageTaskDto> tasks = global
                ? taskRepository.findMaterialUsageRows(startAt, endExclusive)
                : taskRepository.findScopedMaterialUsageRows(factory, areas, startAt, endExclusive);
        List<MaterialUsageMappingDto> mappings = global
                ? mappingRepository.findMaterialUsageRows()
                : mappingRepository.findScopedMaterialUsageRows(factory, areas);
        return new MaterialUsageDashboardDto(
                start,
                end,
                BUSINESS_ZONE.getId(),
                tasks,
                mappings,
                "统计以成功扫码生成任务的 createdAt 为准；已物理删除的旧任务无法恢复。"
        );
    }

    private LocalDate parseDate(String value, LocalDate fallback, String field) {
        if (value == null || value.isBlank()) return fallback;
        try { return LocalDate.parse(value.trim()); }
        catch (DateTimeParseException e) { throw new BusinessException(ErrorCode.PARAM_ERROR, field + "格式应为 yyyy-MM-dd"); }
    }
}
