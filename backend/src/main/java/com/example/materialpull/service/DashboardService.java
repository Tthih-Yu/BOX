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

    public DashboardDtos.Dashboard dashboard() {
        List<InventoryEntity> inventory = inventoryRepository.findAll();
        DashboardDtos.Summary s = new DashboardDtos.Summary();
        s.materials = materialRepository.count();
        s.stationMaterials = stationMaterialRepository.count();
        s.boxes = boxRepository.count();
        s.labels = labelRepository.count();
        s.tasksCreated = taskRepository.countByStatus(TaskStatus.CREATED);
        s.tasksProcessing = taskRepository.findByStatusIn(List.of(TaskStatus.ACCEPTED,TaskStatus.PICKING,TaskStatus.PICKED,TaskStatus.DELIVERING,TaskStatus.ARRIVED)).size();
        s.tasksException = taskRepository.countByStatus(TaskStatus.EXCEPTION);
        s.boxesAbnormal = boxRepository.countByStatus(BoxStatus.ABNORMAL);
        s.lowStockMaterials = BigDecimal.valueOf(inventory.stream().filter(this::isLowStock).count());

        List<TaskStatus> active = List.of(TaskStatus.CREATED, TaskStatus.ACCEPTED, TaskStatus.PICKING, TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED, TaskStatus.EXCEPTION);
        List<ReplenishmentTaskEntity> activeTasks = taskRepository.findByStatusIn(active);
        LocalDateTime now = LocalDateTime.now();
        List<ReplenishmentTaskEntity> timeout = activeTasks.stream().filter(t -> t.getDeadlineAt() != null && t.getDeadlineAt().isBefore(now)).sorted(taskOrder()).limit(50).toList();
        List<ReplenishmentTaskEntity> urgent = activeTasks.stream().filter(t -> t.getPriority() == PriorityLevel.URGENT).sorted(taskOrder()).limit(50).toList();
        List<ReplenishmentTaskEntity> normal = activeTasks.stream().filter(t -> t.getPriority() != PriorityLevel.URGENT && !(t.getDeadlineAt() != null && t.getDeadlineAt().isBefore(now))).sorted(taskOrder()).limit(50).toList();
        List<InventoryEntity> shortage = inventory.stream().filter(this::isLowStock).limit(50).toList();
        s.urgentTasks = urgent.size();
        s.timeoutTasks = timeout.size();
        s.shortageItems = shortage.size();

        DashboardDtos.Dashboard d = new DashboardDtos.Dashboard();
        d.summary = s;
        d.taskStatus = Arrays.stream(TaskStatus.values()).map(x -> new DashboardDtos.ChartItem(x.label, taskRepository.countByStatus(x))).toList();
        d.boxStatus = Arrays.stream(BoxStatus.values()).map(x -> new DashboardDtos.ChartItem(x.label, boxRepository.countByStatus(x))).toList();
        d.latestTasks = taskRepository.findTop20ByOrderByCreatedAtDesc();
        d.latestScans = scanLogRepository.findTop1000ByOrderByScanAtDesc().stream().limit(20).toList();
        d.warnings = shortage;
        d.normalTasks = normal;
        d.timeoutTasks = timeout;
        d.urgentTasks = urgent;
        d.shortageItems = shortage;
        return d;
    }

    private Comparator<ReplenishmentTaskEntity> taskOrder() {
        return Comparator.comparing(ReplenishmentTaskEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
    }

    private boolean isLowStock(InventoryEntity x) {
        return safe(x.getAvailableQty()).compareTo(safe(x.getSafetyStock())) < 0;
    }

    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
