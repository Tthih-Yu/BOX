package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MaterialUsageDashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ReplenishmentTaskRepository taskRepository;
    private final MaterialMappingRepository mappingRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(String from, String to) {
        LocalDate end = parseDate(to, LocalDate.now(BUSINESS_ZONE), "结束日期");
        LocalDate start = parseDate(from, end.minusDays(29), "开始日期");
        if (start.isAfter(end)) throw new BusinessException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        List<ReplenishmentTaskEntity> tasks = taskRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startAt, endExclusive);
        List<MaterialMappingEntity> mappings = mappingRepository.findAllByOrderByLineMaterialCodeAscMappingOrderAscIdAsc();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start);
        result.put("to", end);
        result.put("timezone", BUSINESS_ZONE.getId());
        result.put("tasks", tasks);
        result.put("mappings", mappings);
        result.put("historyNote", "统计以成功扫码生成任务的 createdAt 为准；已物理删除的旧任务无法恢复。");
        return result;
    }

    private LocalDate parseDate(String value, LocalDate fallback, String field) {
        if (value == null || value.isBlank()) return fallback;
        try { return LocalDate.parse(value.trim()); }
        catch (DateTimeParseException e) { throw new BusinessException(ErrorCode.PARAM_ERROR, field + "格式应为 yyyy-MM-dd"); }
    }
}
