package com.example.materialpull.maintenance;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.dto.maintenance.HealthDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import com.example.materialpull.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecoveryService {
    private final ReplenishmentTaskRepository taskRepository;
    private final BoxRepository boxRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditService auditService;
    private final SystemAlertService alertService;
    private final AppProperties properties;

    @Transactional
    public HealthDtos.RecoveryResult markStuckTasks(String operator) {
        HealthDtos.RecoveryResult r = new HealthDtos.RecoveryResult();
        List<TaskStatus> active = List.of(TaskStatus.CREATED, TaskStatus.ACCEPTED, TaskStatus.PICKING, TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED);
        LocalDateTime before = LocalDateTime.now().minusMinutes(properties.getStuckTaskMinutes());
        List<ReplenishmentTaskEntity> stuck = taskRepository.findByStatusInAndUpdatedAtBefore(active, before).stream().limit(properties.getRecoverBatchSize()).toList();
        r.scanned = stuck.size();
        for (ReplenishmentTaskEntity t : stuck) {
            t.setPriority(PriorityLevel.URGENT);
            t.setStuckFlag(true);
            t.setWarningAt(LocalDateTime.now());
            t.setLastError("任务超过 " + properties.getStuckTaskMinutes() + " 分钟未更新，系统已标记防卡死");
            taskRepository.save(t);
            auditService.task(t.getTaskNo(), "MARK_STUCK", t.getStatus().name(), t.getStatus().name(), operator, t.getLastError());
            alertService.open("WARN", "TASK_STUCK", t.getTaskNo(), "补货任务疑似卡住", t.getLastError());
            r.fixed++;
            r.messages.add("已标记卡住任务：" + t.getTaskNo());
        }
        return r;
    }

    @Transactional
    public HealthDtos.RecoveryResult rebuildInventoryAvailable(String operator) {
        HealthDtos.RecoveryResult r = new HealthDtos.RecoveryResult();
        for (InventoryEntity inv : inventoryRepository.findAll()) {
            r.scanned++;
            BigDecimal stock = inv.getStockQty() == null ? BigDecimal.ZERO : inv.getStockQty();
            BigDecimal locked = inv.getLockedQty() == null ? BigDecimal.ZERO : inv.getLockedQty();
            BigDecimal available = stock.subtract(locked);
            if (inv.getAvailableQty() == null || inv.getAvailableQty().compareTo(available) != 0) {
                inv.setAvailableQty(available);
                inv.setLastCheckedAt(LocalDateTime.now());
                inventoryRepository.save(inv);
                r.fixed++;
                r.messages.add("已重算库存：" + inv.getWarehouseMaterialCode() + " 可用=" + available);
            }
        }
        return r;
    }

    @Transactional
    public HealthDtos.RecoveryResult lockBrokenPairs(String operator) {
        HealthDtos.RecoveryResult r = new HealthDtos.RecoveryResult();
        Map<String, List<BoxEntity>> grouped = boxRepository.findAll().stream().collect(Collectors.groupingBy(b -> b.getPairCode() == null ? "__EMPTY_PAIR__" : b.getPairCode()));
        grouped.forEach((pair, boxes) -> {
            r.scanned++;
            long inUse = boxes.stream().filter(b -> b.getStatus() == BoxStatus.IN_USE).count();
            if ("__EMPTY_PAIR__".equals(pair) || pair.isBlank() || boxes.size() != 2 || inUse != 1) {
                for (BoxEntity b : boxes) {
                    b.setHealthStatus("NEED_REVIEW");
                    b.setLastError("AB盒配对或状态需要人工复核");
                    boxRepository.save(b);
                }
                r.warned++;
                r.messages.add("已标记AB配对复核：" + pair);
                alertService.open("WARN", "BOX_PAIR", pair, "AB盒配对需要人工复核", "数量=" + boxes.size() + "，IN_USE=" + inUse);
            }
        });
        return r;
    }
}
