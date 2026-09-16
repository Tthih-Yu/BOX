package com.example.materialpull.dto;

import java.time.LocalDate;
import java.util.List;

public record MaterialUsageDashboardDto(
        LocalDate from,
        LocalDate to,
        String timezone,
        List<MaterialUsageTaskDto> tasks,
        List<MaterialUsageMappingDto> mappings,
        String historyNote
) {}
