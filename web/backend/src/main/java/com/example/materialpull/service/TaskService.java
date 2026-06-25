package com.example.materialpull.service;

import com.example.materialpull.common.*;
import com.example.materialpull.dto.TaskActionRequest;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import com.example.materialpull.resilience.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final ReplenishmentTaskRepository taskRepository;
    private final BoxRepository boxRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditService auditService;
    private final RealtimePushService pushService;
    private final SystemAlertService alertService;
    private final BusinessLockService lockService;
    private final IdempotencyService idempotencyService;
    private final OperationGuard guard;
    private final AppProperties properties;
    private final PrintJobService printJobService;
    private final BoxPoolService boxPoolService;
    private final AgvService agvService;

    public List<ReplenishmentTaskEntity> list(String status) {
        if (status == null || status.isBlank()) return taskRepository.findTop1000ByOrderByCreatedAtDesc();
        TaskStatus s;
        try {
            s = TaskStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知任务状态：" + status);
        }
        return taskRepository.findTop1000ByStatusOrderByCreatedAtDesc(s);
    }

    @Transactional
    public ReplenishmentTaskEntity action(String taskNo, String action, TaskActionRequest req) {
        if (req == null) req = new TaskActionRequest();
        taskNo = guard.notBlank(taskNo, "任务号");
        action = guard.notBlank(action, "操作");
        checkActionPermission(action);
        req.operator = OperatorResolver.currentOperator();
        String requestKey = firstNonBlank(req.requestId, RequestContext.getTraceId());
        String requestHash = RequestDigest.sha256(taskNo, action, req.operator, req.remark, req.exceptionReason, req.receiveScanCode, req.emptyContainerNo, req.expectedStatus, RequestDigest.valueOf(req.force));
        try {
            idempotencyService.begin(requestKey, "TASK_ACTION", taskNo + ":" + action, requestHash);
            String finalTaskNo = taskNo;
            String finalAction = action;
            TaskActionRequest finalReq = req;
            ReplenishmentTaskEntity result = lockService.execute("TASK:" + finalTaskNo, () -> doAction(finalTaskNo, finalAction, finalReq));
            idempotencyService.finish(requestKey, result.getTaskNo(), "任务操作完成：" + action);
            return result;
        } catch (RuntimeException e) {
            if (!(e instanceof BusinessException be && be.getErrorCode() == ErrorCode.DUPLICATE_REQUEST)) idempotencyService.fail(requestKey, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public ReplenishmentTaskEntity receiveBySiteScan(String taskNo, TaskActionRequest req) {
        if (req == null) req = new TaskActionRequest();
        if (req.requestId == null || req.requestId.isBlank()) req.requestId = RequestContext.getTraceId();
        return action(taskNo, "receive", req);
    }

    private ReplenishmentTaskEntity doAction(String taskNo, String action, TaskActionRequest req) {
        ReplenishmentTaskEntity t = taskRepository.findByTaskNoForUpdate(taskNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在：" + taskNo));
        TaskStatus from = t.getStatus();
        if (req.expectedStatus != null && !req.expectedStatus.isBlank() && !Objects.equals(req.expectedStatus, from.name())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "任务状态已变化，页面状态=" + req.expectedStatus + "，当前状态=" + from.name() + "，请刷新后重试");
        }
        if (isTerminal(from) && !List.of("archive", "remark", "returnEmptyBox").contains(action)) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "任务已结束，禁止继续操作：" + from);
        }

        switch (action) {
            case "accept" -> {
                require(from, TaskStatus.CREATED);
                t.setStatus(TaskStatus.ACCEPTED);
                t.setAcceptedBy(req.operator);
                t.setAcceptedAt(LocalDateTime.now());
                BoxPoolEntity container = boxPoolService.allocateForTask(t);
                t.setContainerNo(container.getContainerNo());
                PrintJobEntity printJob = printJobService.createForTask(t, req.operator);
                if (printJob != null) t.setPrintJobNo(printJob.getPrintJobNo());
            }
            case "startPick" -> {
                require(from, TaskStatus.ACCEPTED);
                lockInventory(t);
                t.setStatus(TaskStatus.PICKING);
                t.setPicker(req.operator);
            }
            case "picked" -> {
                require(from, TaskStatus.PICKING, TaskStatus.ACCEPTED);
                if (from == TaskStatus.ACCEPTED) lockInventory(t);
                t.setStatus(TaskStatus.PICKED);
                t.setPicker(req.operator);
                t.setPickedAt(LocalDateTime.now());
            }
            case "deliver" -> {
                require(from, TaskStatus.PICKED);
                t.setStatus(TaskStatus.DELIVERING);
                t.setDeliverer(req.operator);
                t.setDeliveredAt(LocalDateTime.now());
                markBox(t, BoxStatus.IN_TRANSIT, null);
                boxPoolService.markDelivering(t);
                if (!Boolean.TRUE.equals(t.getAgvDispatched())) {
                    String containerNo = firstNonBlank(t.getContainerNo(), boxPoolService.findContainerNoForTask(t.getTaskNo()));
                    AgvJobEntity agv = agvService.dispatchForTask(t, containerNo,
                            firstNonBlank(t.getWarehouseAddress(), t.getWarehouseLocation(), t.getWarehouseCode()),
                            firstNonBlank(t.getSendStationAddress(), t.getDeliveryAddress(), t.getStationCode()),
                            "DELIVER_FULL_BOX", req.operator);
                    t.setAgvJobNo(agv.getAgvJobNo());
                    t.setAgvDispatched(true);
                }
            }
            case "arrive" -> {
                require(from, TaskStatus.DELIVERING);
                t.setStatus(TaskStatus.ARRIVED);
                t.setArrivedAt(LocalDateTime.now());
                markBox(t, BoxStatus.REPLENISHING, null);
                agvService.markArrivedByTask(t.getTaskNo());
            }
            case "complete" -> {
                require(from, TaskStatus.ARRIVED, TaskStatus.DELIVERING, TaskStatus.PICKED);
                if (!Boolean.TRUE.equals(t.getInventoryLocked())) lockInventory(t);
                deductInventory(t);
                t.setStatus(TaskStatus.COMPLETED);
                t.setCompletedAt(LocalDateTime.now());
                if (t.getReceivedAt() == null) t.setReceivedAt(LocalDateTime.now());
                completeBox(t);
                boxPoolService.markDeliveredToLine(t);
                agvService.markArrivedByTask(t.getTaskNo());
            }
            case "receive" -> {
                require(from, TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED);
                if (!Boolean.TRUE.equals(t.getInventoryLocked())) lockInventory(t);
                deductInventory(t);
                t.setStatus(TaskStatus.COMPLETED);
                t.setReceivedBy(req.operator);
                t.setReceiveScanCode(req.receiveScanCode);
                t.setEmptyContainerNo(req.emptyContainerNo);
                t.setReceivedAt(LocalDateTime.now());
                t.setCompletedAt(LocalDateTime.now());
                t.setEmptyBoxReturnRequired(req.emptyContainerNo != null && !req.emptyContainerNo.isBlank());
                completeBox(t);
                boxPoolService.markDeliveredToLine(t);
                agvService.markArrivedByTask(t.getTaskNo());
                if (req.emptyContainerNo != null && !req.emptyContainerNo.isBlank()) {
                    boxPoolService.returnEmpty(req.emptyContainerNo, t.getTaskNo(), firstNonBlank(t.getSendStationAddress(), t.getDeliveryAddress(), t.getStationCode()), req.operator);
                    agvService.dispatchForTask(t, req.emptyContainerNo,
                            firstNonBlank(t.getSendStationAddress(), t.getDeliveryAddress(), t.getStationCode()),
                            firstNonBlank(t.getWarehouseAddress(), t.getWarehouseLocation(), t.getWarehouseCode(), "仓库空箱区"),
                            "RETURN_EMPTY_BOX", req.operator);
                }
            }
            case "returnEmptyBox" -> {
                if (req.emptyContainerNo == null || req.emptyContainerNo.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "空盒编号不能为空");
                boxPoolService.returnEmpty(req.emptyContainerNo, t.getTaskNo(), firstNonBlank(t.getSendStationAddress(), t.getDeliveryAddress(), t.getStationCode()), req.operator);
                agvService.dispatchForTask(t, req.emptyContainerNo,
                        firstNonBlank(t.getSendStationAddress(), t.getDeliveryAddress(), t.getStationCode()),
                        firstNonBlank(t.getWarehouseAddress(), t.getWarehouseLocation(), t.getWarehouseCode(), "仓库空箱区"),
                        "RETURN_EMPTY_BOX", req.operator);
                t.setEmptyContainerNo(req.emptyContainerNo);
                t.setEmptyBoxReturnRequired(true);
            }
            case "cancel" -> {
                if (from == TaskStatus.COMPLETED) throw new BusinessException(ErrorCode.STATE_CONFLICT, "已完成任务不能取消");
                releaseInventory(t);
                boxPoolService.releaseByTaskNo(t.getTaskNo(), req.operator);
                printJobService.cancelByTaskNo(t.getTaskNo(), req.operator);
                agvService.cancelByTaskNo(t.getTaskNo(), req.operator);
                t.setAgvDispatched(false);
                t.setStatus(TaskStatus.CANCELLED);
                if (req.remark == null || req.remark.isBlank()) req.remark = "任务取消，已释放库存锁定、周转箱、打印任务和AGV任务";
                t.setRemark(req.remark);
            }
            case "exception" -> {
                t.setStatus(TaskStatus.EXCEPTION);
                t.setExceptionReason(firstNonBlank(req.exceptionReason, req.remark, "人工标记异常"));
                t.setLastError(t.getExceptionReason());
                markBox(t, BoxStatus.ABNORMAL, t.getExceptionReason());
                alertService.open("ERROR", "TASK", t.getTaskNo(), "补货任务异常", t.getExceptionReason());
            }
            case "retry" -> {
                require(from, TaskStatus.EXCEPTION);
                t.setRetryCount((t.getRetryCount() == null ? 0 : t.getRetryCount()) + 1);
                t.setExceptionReason(null);
                t.setLastError(null);
                t.setStatus(TaskStatus.ACCEPTED);
                markBox(t, BoxStatus.EMPTY_WAITING_PULL, null);
            }
            case "forceComplete" -> {
                if (!Boolean.TRUE.equals(req.force)) throw new BusinessException(ErrorCode.FORBIDDEN, "强制完成必须传 force=true");
                if (!Boolean.TRUE.equals(t.getInventoryDeducted())) deductInventory(t);
                t.setStatus(TaskStatus.COMPLETED);
                t.setCompletedAt(LocalDateTime.now());
                if (t.getReceivedAt() == null) t.setReceivedAt(LocalDateTime.now());
                completeBox(t);
                boxPoolService.markDeliveredToLine(t);
                agvService.markArrivedByTask(t.getTaskNo());
            }
            case "remark" -> {
                if (req.remark == null || req.remark.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "备注内容不能为空");
                t.setRemark(req.remark.trim());
            }
            case "archive" -> {
                if (!isTerminal(from)) throw new BusinessException(ErrorCode.STATE_CONFLICT, "只有结束任务才能归档备注");
                if (req.remark != null && !req.remark.isBlank()) t.setRemark(req.remark.trim());
            }
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "未知操作：" + action);
        }

        if (req.remark != null && !req.remark.isBlank()) t.setRemark(req.remark);
        t.setActionSeq((t.getActionSeq() == null ? 0 : t.getActionSeq()) + 1);
        t.setLastActionAt(LocalDateTime.now());
        t.setStuckFlag(false);
        taskRepository.save(t);
        auditService.task(t.getTaskNo(), action, from.name(), t.getStatus().name(), req.operator, req.remark);
        pushService.publish("tasks", t);
        return t;
    }

    private void checkActionPermission(String action) {
        switch (action) {
            case "accept", "startPick", "picked", "deliver", "arrive", "complete", "cancel", "retry" -> RequestContext.requireAnyRole(UserRole.WAREHOUSE);
            case "receive", "returnEmptyBox", "exception", "remark", "archive" -> RequestContext.requireAnyRole(UserRole.WAREHOUSE, UserRole.LINE);
            case "forceComplete" -> RequestContext.requireAnyRole(UserRole.ADMIN);
            default -> { }
        }
    }

    private void require(TaskStatus current, TaskStatus... allowed) {
        for (TaskStatus s : allowed) if (current == s) return;
        throw new BusinessException(ErrorCode.STATE_CONFLICT, "当前状态不允许执行该操作：" + current);
    }

    private boolean isTerminal(TaskStatus s) { return s == TaskStatus.COMPLETED || s == TaskStatus.CANCELLED; }

    private void markBox(ReplenishmentTaskEntity t, BoxStatus status, String reason) {
        if (t.getBoxCode() == null || t.getBoxCode().isBlank()) return;
        boxRepository.findByBoxCodeForUpdate(t.getBoxCode()).ifPresent(b -> {
            b.setStatus(status);
            if (status == BoxStatus.ABNORMAL) {
                b.setHealthStatus("ABNORMAL");
                b.setLockReason(reason);
                b.setLockedAt(LocalDateTime.now());
                b.setLastError(reason);
            } else {
                b.setHealthStatus("OK");
                b.setLockReason(null);
                b.setLastError(null);
            }
            boxRepository.save(b);
            pushService.publish("boxes", b);
        });
    }

    private void completeBox(ReplenishmentTaskEntity t) {
        if (t.getBoxCode() == null || t.getBoxCode().isBlank()) return;
        BoxEntity b = boxRepository.findByBoxCodeForUpdate(t.getBoxCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "盒子不存在：" + t.getBoxCode()));
        b.setStatus(BoxStatus.FULL_STANDBY);
        b.setCurrentQty(b.getStandardQty());
        b.setLastReplenishedAt(LocalDateTime.now());
        b.setHealthStatus("OK");
        b.setLockReason(null);
        b.setLastError(null);
        boxRepository.save(b);
        pushService.publish("boxes", b);
    }

    private void lockInventory(ReplenishmentTaskEntity t) {
        if (Boolean.TRUE.equals(t.getInventoryLocked())) return;
        InventoryEntity inv = chooseInventory(t.getWarehouseMaterialCode());
        if (Boolean.TRUE.equals(inv.getFrozen())) throw new BusinessException(ErrorCode.INVENTORY_SHORTAGE, "库存已冻结：" + inv.getFreezeReason());
        BigDecimal qty = positiveQty(t.getRequestQty(), "任务需求数量");
        BigDecimal available = safe(inv.getStockQty()).subtract(safe(inv.getLockedQty()));
        if (available.compareTo(qty) < 0) {
            String msg = "库存不足：仓库料号=" + t.getWarehouseMaterialCode() + "，可用=" + available + "，需求=" + qty;
            alertService.open("WARN", "INVENTORY", t.getTaskNo(), "补货任务库存不足", msg);
            if (properties.isStrictInventory()) throw new BusinessException(ErrorCode.INVENTORY_SHORTAGE, msg);
        }
        inv.setLockedQty(safe(inv.getLockedQty()).add(qty));
        inventoryRepository.save(inv);
        t.setInventoryLocked(true);
        t.setLockedQty(qty);
    }

    private void releaseInventory(ReplenishmentTaskEntity t) {
        if (!Boolean.TRUE.equals(t.getInventoryLocked()) || Boolean.TRUE.equals(t.getInventoryDeducted())) return;
        InventoryEntity inv = chooseInventory(t.getWarehouseMaterialCode());
        inv.setLockedQty(safe(inv.getLockedQty()).subtract(safe(t.getLockedQty())).max(BigDecimal.ZERO));
        inventoryRepository.save(inv);
        t.setInventoryLocked(false);
        t.setLockedQty(BigDecimal.ZERO);
    }

    private void deductInventory(ReplenishmentTaskEntity t) {
        if (Boolean.TRUE.equals(t.getInventoryDeducted())) return;
        InventoryEntity inv = chooseInventory(t.getWarehouseMaterialCode());
        BigDecimal qty = positiveQty(t.getRequestQty(), "任务需求数量");
        if (safe(inv.getStockQty()).compareTo(qty) < 0) {
            String msg = "库存扣减失败：仓库料号=" + t.getWarehouseMaterialCode() + "，账面库存=" + inv.getStockQty() + "，需求=" + qty;
            alertService.open("ERROR", "INVENTORY", t.getTaskNo(), "库存扣减失败", msg);
            throw new BusinessException(ErrorCode.INVENTORY_SHORTAGE, msg);
        }
        inv.setStockQty(safe(inv.getStockQty()).subtract(qty));
        if (Boolean.TRUE.equals(t.getInventoryLocked())) inv.setLockedQty(safe(inv.getLockedQty()).subtract(safe(t.getLockedQty())).max(BigDecimal.ZERO));
        inventoryRepository.save(inv);
        t.setInventoryLocked(false);
        t.setInventoryDeducted(true);
        t.setLockedQty(BigDecimal.ZERO);
        if (safe(inv.getStockQty()).compareTo(safe(inv.getSafetyStock())) < 0) {
            alertService.open("WARN", "INVENTORY", t.getWarehouseMaterialCode(), "库存低于安全库存", "库存=" + inv.getStockQty() + "，安全库存=" + inv.getSafetyStock());
        }
    }

    private InventoryEntity chooseInventory(String warehouseMaterialCode) {
        if (warehouseMaterialCode == null || warehouseMaterialCode.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "任务缺少仓库料号，无法锁定或扣减库存");
        List<InventoryEntity> list = inventoryRepository.findByWarehouseMaterialCodeForUpdate(warehouseMaterialCode.trim());
        if (list.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "未找到库存记录：" + warehouseMaterialCode);
        return list.get(0);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }

    private BigDecimal positiveQty(BigDecimal v, String name) {
        BigDecimal value = safe(v);
        if (value.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR, name + "必须大于0");
        return value;
    }

    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
