package com.example.materialpull.maintenance;

import com.example.materialpull.dto.maintenance.HealthDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import com.example.materialpull.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataQualityService {
    private final BoxRepository boxRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final InventoryRepository inventoryRepository;
    private final LabelRepository labelRepository;
    private final SystemAlertService alertService;

    public HealthDtos.HealthReport checkFactoryHealth(boolean createAlerts) {
        HealthDtos.HealthReport report = new HealthDtos.HealthReport();
        checkBoxPairs(report, createAlerts);
        checkDuplicateActiveTasks(report, createAlerts);
        checkInventory(report, createAlerts);
        checkLabelBindings(report, createAlerts);
        report.openAlertCount = (int) alertService.openCount();
        report.errorCount = (int) report.items.stream().filter(x -> "ERROR".equals(x.level)).count();
        report.warningCount = (int) report.items.stream().filter(x -> "WARN".equals(x.level)).count();
        report.status = report.errorCount > 0 ? "DOWN" : (report.warningCount > 0 ? "WARN" : "UP");
        return report;
    }

    private void checkBoxPairs(HealthDtos.HealthReport report, boolean createAlerts) {
        Map<String, List<BoxEntity>> grouped = boxRepository.findAll().stream().collect(Collectors.groupingBy(b -> b.getPairCode() == null ? "__EMPTY_PAIR__" : b.getPairCode()));
        grouped.forEach((pair, boxes) -> {
            if ("__EMPTY_PAIR__".equals(pair) || pair.isBlank()) add(report, createAlerts, "BOX_PAIR_EMPTY", "ERROR", "盒子缺少pairCode", "发现未绑定AB配对编码的盒子", boxes);
            else if (boxes.size() != 2) add(report, createAlerts, "BOX_PAIR_COUNT", "ERROR", "AB配对数量异常", pair + " 下盒子数量=" + boxes.size(), pair);
            long inUse = boxes.stream().filter(b -> b.getStatus() == BoxStatus.IN_USE).count();
            if (boxes.size() == 2 && inUse != 1) add(report, createAlerts, "BOX_IN_USE_COUNT", "WARN", "AB盒使用状态需要复核", pair + " 正在使用的盒子数量=" + inUse, pair);
        });
    }

    private void checkDuplicateActiveTasks(HealthDtos.HealthReport report, boolean createAlerts) {
        List<TaskStatus> active = List.of(TaskStatus.CREATED, TaskStatus.ACCEPTED, TaskStatus.PICKING, TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED, TaskStatus.EXCEPTION);
        Map<String, List<ReplenishmentTaskEntity>> grouped = taskRepository.findByStatusIn(active).stream()
                .filter(t -> t.getSourceLabelCode() != null)
                .collect(Collectors.groupingBy(ReplenishmentTaskEntity::getSourceLabelCode));
        grouped.forEach((label, tasks) -> {
            if (tasks.size() > 1) add(report, createAlerts, "DUP_ACTIVE_TASK", "ERROR", "同一标签存在多个未完成任务", label + " 未完成任务数量=" + tasks.size(), label);
        });
    }

    private void checkInventory(HealthDtos.HealthReport report, boolean createAlerts) {
        for (InventoryEntity inv : inventoryRepository.findAll()) {
            BigDecimal stock = nvl(inv.getStockQty());
            BigDecimal locked = nvl(inv.getLockedQty());
            BigDecimal available = stock.subtract(locked);
            if (available.compareTo(BigDecimal.ZERO) < 0) add(report, createAlerts, "INV_NEGATIVE", "ERROR", "库存可用量为负", inv.getWarehouseMaterialCode() + " 库存=" + stock + " 锁定=" + locked, inv.getWarehouseMaterialCode());
            if (stock.compareTo(nvl(inv.getSafetyStock())) < 0) add(report, createAlerts, "INV_SAFETY", "WARN", "库存低于安全库存", inv.getWarehouseMaterialCode() + " 当前=" + stock + " 安全=" + inv.getSafetyStock(), inv.getWarehouseMaterialCode());
        }
    }

    private void checkLabelBindings(HealthDtos.HealthReport report, boolean createAlerts) {
        Set<String> boxCodes = boxRepository.findAll().stream().map(BoxEntity::getBoxCode).collect(Collectors.toSet());
        for (LabelEntity label : labelRepository.findAll()) {
            // 真实工厂拉动标签、POINT OF USE 标签允许不绑定 A/B 盒；只有明确写入 boxCode 的标签才检查盒子是否存在。
            if (label.getBoxCode() == null || label.getBoxCode().isBlank()) continue;
            if (!boxCodes.contains(label.getBoxCode())) add(report, createAlerts, "LABEL_BINDING", "ERROR", "标签绑定盒子不存在", label.getLabelCode() + " -> " + label.getBoxCode(), label.getLabelCode());
        }
    }

    private void add(HealthDtos.HealthReport report, boolean createAlerts, String code, String level, String title, String msg, Object detail) {
        report.items.add(new HealthDtos.CheckItem(code, level, title, msg, detail));
        if (createAlerts) alertService.open(level, "DATA_CHECK", String.valueOf(detail), title, msg);
    }

    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
