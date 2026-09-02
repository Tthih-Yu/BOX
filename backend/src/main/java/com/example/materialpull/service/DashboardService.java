package com.example.materialpull.service;

import com.example.materialpull.dto.DashboardDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final MaterialRepository materialRepository;
    private final StationMaterialRepository stationMaterialRepository;
    private final BoxRepository boxRepository;
    private final LabelRepository labelRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final ScanLogRepository scanLogRepository;
    private final InventoryRepository inventoryRepository;
    private final DataScopeService dataScopeService;

    public DashboardDtos.Dashboard dashboard(String timeRange) {
        LocalDateTime startTime = calculateStartTime(timeRange);
        
        boolean global = dataScopeService.isGlobalAdmin();
        String factory = global ? null : dataScopeService.currentFactory();
        List<String> areas = global ? List.of() : dataScopeService.currentDeliveryAreas();
        List<InventoryEntity> shortage = global
                ? inventoryRepository.findLowStock()
                : inventoryRepository.findLowStockScoped(factory, areas);
        List<ReplenishmentTaskEntity> activeTasks = global
                ? taskRepository.findByStatusIn(activeStatuses())
                : taskRepository.findByFactoryIgnoreCaseAndDeliveryAreaInAndStatusIn(factory, areas, activeStatuses());
        DashboardDtos.Summary s = new DashboardDtos.Summary();
        s.materials = materialRepository.count();
        s.stationMaterials = stationMaterialRepository.count();
        s.boxes = global ? boxRepository.count() : boxRepository.countByFactoryIgnoreCaseAndDeliveryAreaIn(factory, areas);
        s.labels = global ? labelRepository.count() : labelRepository.countByFactoryIgnoreCaseAndDeliveryAreaIn(factory, areas);
        s.tasksCreated = countTasks(TaskStatus.CREATED, global, factory, areas);
        s.tasksProcessing = activeTasks.stream().filter(t -> List.of(TaskStatus.ACCEPTED, TaskStatus.PICKING,
                TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED).contains(t.getStatus())).count();
        s.tasksException = countTasks(TaskStatus.EXCEPTION, global, factory, areas);
        s.boxesAbnormal = global ? boxRepository.countByStatus(BoxStatus.ABNORMAL)
                : boxRepository.countByFactoryIgnoreCaseAndDeliveryAreaInAndStatus(factory, areas, BoxStatus.ABNORMAL);
        s.lowStockMaterials = BigDecimal.valueOf(shortage.size());

        LocalDateTime now = LocalDateTime.now();
        List<ReplenishmentTaskEntity> timeout = activeTasks.stream().filter(t -> t.getDeadlineAt() != null && t.getDeadlineAt().isBefore(now)).sorted(taskOrder()).limit(50).toList();
        List<ReplenishmentTaskEntity> urgent = activeTasks.stream().filter(t -> t.getPriority() == PriorityLevel.URGENT).sorted(taskOrder()).limit(50).toList();
        List<ReplenishmentTaskEntity> normal = activeTasks.stream().filter(t -> t.getPriority() != PriorityLevel.URGENT && !(t.getDeadlineAt() != null && t.getDeadlineAt().isBefore(now))).sorted(taskOrder()).limit(50).toList();
        shortage = shortage.stream().limit(50).toList();
        s.urgentTasks = urgent.size();
        s.timeoutTasks = timeout.size();
        s.shortageItems = shortage.size();

        DashboardDtos.Dashboard d = new DashboardDtos.Dashboard();
        d.summary = s;
        d.taskStatus = Arrays.stream(TaskStatus.values()).map(x -> new DashboardDtos.ChartItem(x.label,
                countTasksByStatusAndTime(x, startTime, global, factory, areas))).toList();
        d.boxStatus = Arrays.stream(BoxStatus.values()).map(x -> new DashboardDtos.ChartItem(x.label,
                global ? boxRepository.countByStatus(x)
                        : boxRepository.countByFactoryIgnoreCaseAndDeliveryAreaInAndStatus(factory, areas, x))).toList();
        d.latestTasks = global ? taskRepository.findTop20ByOrderByCreatedAtDesc()
                : taskRepository.findTop20ByFactoryIgnoreCaseAndDeliveryAreaInOrderByCreatedAtDesc(factory, areas);
        d.latestScans = (global ? scanLogRepository.findTop1000ByOrderByScanAtDesc()
                : scanLogRepository.findTop1000ByFactoryIgnoreCaseAndDeliveryAreaInOrderByScanAtDesc(factory, areas))
                .stream().limit(20).toList();
        d.warnings = shortage;
        d.normalTasks = normal;
        d.timeoutTasks = timeout;
        d.urgentTasks = urgent;
        d.shortageItems = shortage;
        return d;
    }

    private LocalDateTime calculateStartTime(String timeRange) {
        LocalDateTime now = LocalDateTime.now();
        return switch (timeRange) {
            case "today" -> now.toLocalDate().atStartOfDay();
            case "3days" -> now.minusDays(3);
            case "week" -> now.minusWeeks(1);
            case "month" -> now.minusMonths(1);
            case "halfyear" -> now.minusMonths(6);
            case "year" -> now.minusYears(1);
            case "all" -> LocalDateTime.of(2000, 1, 1, 0, 0);
            default -> now.toLocalDate().atStartOfDay();
        };
    }

    private long countTasksByStatusAndTime(TaskStatus status, LocalDateTime startTime, boolean global,
                                           String factory, List<String> areas) {
        return global ? taskRepository.countByStatusAndCreatedAtAfter(status, startTime)
                : taskRepository.countByFactoryIgnoreCaseAndDeliveryAreaInAndStatusAndCreatedAtAfter(
                        factory, areas, status, startTime);
    }

    private long countTasks(TaskStatus status, boolean global, String factory, List<String> areas) {
        return global ? taskRepository.countByStatus(status)
                : taskRepository.countByFactoryIgnoreCaseAndDeliveryAreaInAndStatus(factory, areas, status);
    }

    private List<TaskStatus> activeStatuses() {
        return List.of(TaskStatus.CREATED, TaskStatus.ACCEPTED, TaskStatus.PICKING, TaskStatus.PICKED,
                TaskStatus.DELIVERING, TaskStatus.ARRIVED, TaskStatus.EXCEPTION);
    }

    private Comparator<ReplenishmentTaskEntity> taskOrder() {
        return Comparator.comparing(ReplenishmentTaskEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
    }

}
