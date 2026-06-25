package com.example.materialpull.service;

import com.example.materialpull.common.*;
import com.example.materialpull.dto.ScanDtos;
import com.example.materialpull.dto.TaskActionRequest;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import com.example.materialpull.resilience.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScanService {
    private final LabelRepository labelRepository;
    private final LabelResolverService labelResolverService;
    private final BoxRepository boxRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final AuditService auditService;
    private final RealtimePushService pushService;
    private final SystemAlertService alertService;
    private final BusinessLockService lockService;
    private final IdempotencyService idempotencyService;
    private final OperationGuard guard;
    private final AppProperties properties;
    private final TaskService taskService;
    private final ExceptionEventRepository exceptionEventRepository;

    @Value("${app.task.timeout-minutes:120}")
    private long timeoutMinutes;

    @Transactional
    public ScanDtos.ScanResult scanEmpty(ScanDtos.ScanRequest req) {
        if (req == null) req = new ScanDtos.ScanRequest();
        String rawScanCode = req.scanCode == null || req.scanCode.isBlank() ? req.labelCode : req.scanCode;
        rawScanCode = labelResolverService.normalize(rawScanCode);
        req.scanCode = rawScanCode;
        req.labelCode = rawScanCode;
        req.operator = OperatorResolver.currentOperator();
        req.deviceNo = (req.deviceNo == null || req.deviceNo.isBlank()) ? "UNKNOWN" : req.deviceNo.trim();
        String requestKey = firstNonBlank(req.idempotencyKey, RequestContext.getTraceId());
        String requestHash = RequestDigest.sha256(rawScanCode, req.operator, req.deviceNo, req.action, RequestDigest.valueOf(req.allowRepeat));
        try {
            idempotencyService.begin(requestKey, "SCAN_EMPTY", rawScanCode, requestHash);
            ScanDtos.ScanRequest finalReq = req;
            ScanDtos.ScanResult result = lockService.execute("SCAN:" + finalReq.scanCode, () -> doScanEmpty(finalReq));
            idempotencyService.finish(requestKey, result.taskNo, result.message);
            return result;
        } catch (RuntimeException e) {
            if (!(e instanceof BusinessException be && be.getErrorCode() == ErrorCode.DUPLICATE_REQUEST)) {
                idempotencyService.fail(requestKey, e.getMessage());
            }
            throw e;
        }
    }



    @Transactional
    public ScanDtos.ScanResult scanReceive(ScanDtos.ScanRequest req) {
        if (req == null) req = new ScanDtos.ScanRequest();
        req.operator = OperatorResolver.currentOperator();
        req.deviceNo = (req.deviceNo == null || req.deviceNo.isBlank()) ? "UNKNOWN" : req.deviceNo.trim();
        String rawScanCode = req.scanCode == null || req.scanCode.isBlank() ? req.labelCode : req.scanCode;
        rawScanCode = rawScanCode == null ? null : labelResolverService.normalize(rawScanCode);
        req.scanCode = rawScanCode;
        req.labelCode = rawScanCode;
        String taskNo = firstNonBlank(req.taskNo, findReceiveTaskNo(rawScanCode));
        if (taskNo == null || taskNo.isBlank()) throw new BusinessException(ErrorCode.NOT_FOUND, "未找到可收货确认的配送任务");
        TaskActionRequest actionReq = new TaskActionRequest();
                actionReq.receiveScanCode = rawScanCode;
        actionReq.emptyContainerNo = req.emptyContainerNo;
        actionReq.remark = firstNonBlank(req.reason, "现场扫码收货确认，设备=" + req.deviceNo);
        actionReq.requestId = firstNonBlank(req.idempotencyKey, RequestContext.getTraceId());
        ReplenishmentTaskEntity task = taskService.receiveBySiteScan(taskNo, actionReq);
        auditService.scan(firstNonBlank(task.getSourceLabelCode(), rawScanCode), task.getBoxCode(), "RECEIVE", true, "现场收货确认，任务=" + task.getTaskNo() + "，空盒=" + req.emptyContainerNo, req.operator, req.deviceNo, task.getStationCode(), task.getMaterialCode());
        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        r.taskCreated = false;
        r.taskNo = task.getTaskNo();
        r.message = "收货确认完成，任务已闭环；如填写空盒编号，系统已进入空盒回收流程";
        r.scannedCode = rawScanCode;
        r.resolvedLabelCode = task.getSourceLabelCode();
        r.materialCode = task.getMaterialCode();
        r.materialName = task.getMaterialName();
        r.warehouseCode = task.getWarehouseCode();
        r.warehouseAddress = task.getWarehouseAddress();
        r.sendStationAddress = task.getSendStationAddress();
        r.deliveryAddress = task.getDeliveryAddress();
        r.warehouseLocation = task.getWarehouseLocation();
        r.currentBoxCode = task.getBoxCode();
        r.taskStatus = task.getStatus();
        r.priority = task.getPriority() == null ? null : task.getPriority().name();
        r.receiveStatus = "RECEIVED";
        r.agvJobNo = task.getAgvJobNo();
        r.printJobNo = task.getPrintJobNo();
        r.duplicateBlocked = false;
        r.warnings = task.getEmptyContainerNo() == null ? List.of() : List.of("空盒已登记回收：" + task.getEmptyContainerNo());
        r.scanAt = LocalDateTime.now();
        return r;
    }

    @Transactional
    public ScanDtos.ScanResult scanException(ScanDtos.ScanRequest req) {
        if (req == null) req = new ScanDtos.ScanRequest();
        String rawScanCode = req.scanCode == null || req.scanCode.isBlank() ? req.labelCode : req.scanCode;
        rawScanCode = rawScanCode == null ? null : labelResolverService.normalize(rawScanCode);
        req.scanCode = rawScanCode;
        req.labelCode = rawScanCode;
        req.operator = OperatorResolver.currentOperator();
        req.deviceNo = (req.deviceNo == null || req.deviceNo.isBlank()) ? "UNKNOWN" : req.deviceNo.trim();
        String taskNo = firstNonBlank(req.taskNo, findAnyActiveTaskNo(rawScanCode));
        ExceptionEventEntity event = new ExceptionEventEntity();
        event.setEventNo(IdGenerator.id("EXC"));
        event.setSourceType(taskNo == null ? "SITE_SCAN" : "TASK");
        event.setSourceNo(firstNonBlank(taskNo, rawScanCode));
        event.setLevel(firstNonBlank(req.exceptionType, "SITE_EXCEPTION").toUpperCase(Locale.ROOT).contains("SHORT") ? "ERROR" : "WARN");
        event.setTitle(firstNonBlank(req.exceptionType, "现场扫码异常"));
        event.setContent(firstNonBlank(req.reason, "现场扫码异常上报") + "；扫码=" + rawScanCode + "；设备=" + req.deviceNo + "；操作人=" + req.operator);
        event.setOwner(OperatorResolver.currentOperator());
        exceptionEventRepository.save(event);
        ReplenishmentTaskEntity task = null;
        if (taskNo != null) {
            TaskActionRequest actionReq = new TaskActionRequest();
                        actionReq.exceptionReason = event.getContent();
            actionReq.remark = "异常事件=" + event.getEventNo();
            actionReq.requestId = firstNonBlank(req.idempotencyKey, RequestContext.getTraceId());
            task = taskService.action(taskNo, "exception", actionReq);
        }
        auditService.scan(rawScanCode, task == null ? null : task.getBoxCode(), "SITE_EXCEPTION", true, event.getContent(), req.operator, req.deviceNo, task == null ? null : task.getStationCode(), task == null ? null : task.getMaterialCode());
        alertService.open(event.getLevel(), "SITE_EXCEPTION", event.getEventNo(), event.getTitle(), event.getContent());
        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        r.taskCreated = false;
        r.taskNo = taskNo;
        r.exceptionNo = event.getEventNo();
        r.message = "异常已上报，事件号：" + event.getEventNo();
        r.scannedCode = rawScanCode;
        if (task != null) {
            r.resolvedLabelCode = task.getSourceLabelCode();
            r.materialCode = task.getMaterialCode();
            r.materialName = task.getMaterialName();
            r.taskStatus = task.getStatus();
            r.priority = task.getPriority() == null ? null : task.getPriority().name();
        }
        r.duplicateBlocked = false;
        r.warnings = List.of("仓库任务和大屏会显示异常状态");
        r.scanAt = LocalDateTime.now();
        return r;
    }

    private String findReceiveTaskNo(String scanCode) {
        if (scanCode == null || scanCode.isBlank()) return null;
        Optional<ReplenishmentTaskEntity> byTask = taskRepository.findByTaskNo(scanCode);
        if (byTask.isPresent()) return byTask.get().getTaskNo();
        try {
            LabelEntity label = labelResolverService.resolve(scanCode);
            List<TaskStatus> statuses = List.of(TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED);
            List<ReplenishmentTaskEntity> byLabel = taskRepository.findBySourceLabelCodeAndStatusIn(label.getLabelCode(), statuses);
            if (!byLabel.isEmpty()) return latest(byLabel).getTaskNo();
            if (label.getBoxCode() != null) {
                List<ReplenishmentTaskEntity> byBox = taskRepository.findByBoxCodeAndStatusIn(label.getBoxCode(), statuses);
                if (!byBox.isEmpty()) return latest(byBox).getTaskNo();
            }
        } catch (RuntimeException ignored) {}
        return null;
    }

    private String findAnyActiveTaskNo(String scanCode) {
        if (scanCode == null || scanCode.isBlank()) return null;
        Optional<ReplenishmentTaskEntity> byTask = taskRepository.findByTaskNo(scanCode);
        if (byTask.isPresent()) return byTask.get().getTaskNo();
        try {
            LabelEntity label = labelResolverService.resolve(scanCode);
            List<ReplenishmentTaskEntity> byLabel = taskRepository.findBySourceLabelCodeAndStatusIn(label.getLabelCode(), activeStatuses());
            if (!byLabel.isEmpty()) return latest(byLabel).getTaskNo();
        } catch (RuntimeException ignored) {}
        return null;
    }

    private void validateLabelReadyForPull(LabelEntity label) {
        if (label.getLabelCode() == null || label.getLabelCode().isBlank()) throw new BusinessException(ErrorCode.DATA_DIRTY, "标签缺少标签编号，禁止扫码");
        if (label.getMaterialCode() == null || label.getMaterialCode().isBlank()) throw new BusinessException(ErrorCode.DATA_DIRTY, "标签缺少物料编码，禁止扫码：" + label.getLabelCode());
        if (label.getMaterialName() == null || label.getMaterialName().isBlank()) throw new BusinessException(ErrorCode.DATA_DIRTY, "标签缺少物料名称，禁止扫码：" + label.getLabelCode());
        if (label.getWarehouseMaterialCode() == null || label.getWarehouseMaterialCode().isBlank()) throw new BusinessException(ErrorCode.DATA_DIRTY, "标签缺少仓库料号，禁止扫码：" + label.getLabelCode());
        if (label.getStandardQty() == null || label.getStandardQty().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(ErrorCode.DATA_DIRTY, "标签缺少有效单盒数量，禁止扫码：" + label.getLabelCode());
        if (firstNonBlank(label.getSendStationAddress(), label.getDeliveryAddress(), label.getStationCode()) == null) throw new BusinessException(ErrorCode.DATA_DIRTY, "标签缺少送达工位/地址，禁止扫码：" + label.getLabelCode());
    }

    private ReplenishmentTaskEntity latest(List<ReplenishmentTaskEntity> tasks) {
        return tasks.stream().max(Comparator.comparing(ReplenishmentTaskEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))).orElse(tasks.get(0));
    }

    private ScanDtos.ScanResult doScanEmpty(ScanDtos.ScanRequest req) {
        LabelEntity label = labelResolverService.resolveForUpdate(req.scanCode);

        if (label.getStatus() == LabelStatus.VOIDED) throw new BusinessException(ErrorCode.STATE_CONFLICT, "标签已作废，禁止扫码");
        validateLabelReadyForPull(label);
        label.setLastScannedAt(LocalDateTime.now());
        label.setLastScanDevice(req.deviceNo);
        label.setLastScanOperator(req.operator);
        labelRepository.save(label);
        String resolvedLabelCode = label.getLabelCode();

        Optional<BoxEntity> boxOptional = boxRepository.findByLabelCodeForUpdate(label.getLabelCode());
        if (boxOptional.isEmpty()) {
            return doDirectPull(req, label);
        }
        BoxEntity box = boxOptional.get();
        if (box.getStatus() == BoxStatus.ABNORMAL || box.getStatus() == BoxStatus.SCRAPPED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "盒子处于异常或报废状态，禁止扫码：" + box.getStatus());
        }

        List<TaskStatus> active = activeStatuses();
        List<ReplenishmentTaskEntity> existing = taskRepository.findBySourceLabelCodeAndStatusIn(resolvedLabelCode, active);
        if (!existing.isEmpty() && !Boolean.TRUE.equals(req.allowRepeat)) {
            ReplenishmentTaskEntity t = existing.stream().max(Comparator.comparing(ReplenishmentTaskEntity::getCreatedAt)).orElse(existing.get(0));
            auditService.scan(resolvedLabelCode, box.getBoxCode(), "EMPTY_DUPLICATE", true, "重复扫码被拦截，返回已有任务：" + t.getTaskNo() + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, box.getStationCode(), box.getMaterialCode());
            return duplicateResult(box, t, "该标签已经存在未完成补货任务，系统已阻止重复生成：" + t.getTaskNo());
        }

        List<BoxEntity> pair = boxRepository.findByPairCodeForUpdate(box.getPairCode());
        if (pair.size() != 2) {
            box.setStatus(BoxStatus.ABNORMAL);
            box.setHealthStatus("PAIR_BROKEN");
            box.setLockReason("AB配对数量不是2");
            box.setLockedAt(LocalDateTime.now());
            boxRepository.save(box);
            alertService.open("ERROR", "BOX_PAIR", box.getPairCode(), "AB双盒配对异常", "配对编码 " + box.getPairCode() + " 下盒子数量为 " + pair.size());
            auditService.scan(resolvedLabelCode, box.getBoxCode(), "EMPTY", false, "AB配对异常，原始扫码=" + req.scanCode, req.operator, req.deviceNo, box.getStationCode(), box.getMaterialCode());
            throw new BusinessException(ErrorCode.DATA_DIRTY, "AB配对异常，请维护盒子数据：" + box.getPairCode());
        }

        BoxEntity current = pair.stream().filter(x -> Objects.equals(x.getBoxCode(), box.getBoxCode())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_DIRTY, "当前盒不在AB配对中"));
        BoxEntity standby = pair.stream().filter(x -> !Objects.equals(x.getBoxCode(), box.getBoxCode())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_DIRTY, "未找到AB备用盒"));

        if (current.getStatus() != BoxStatus.IN_USE && current.getStatus() == BoxStatus.FULL_STANDBY) {
            return doSpareUrgentPull(req, label, current, standby, pair);
        }

        if (current.getStatus() != BoxStatus.IN_USE) {
            String msg = "当前扫码盒不是正在使用状态，当前状态：" + current.getStatus();
            current.setLastError(msg);
            current.setHealthStatus("STATE_CONFLICT");
            boxRepository.save(current);
            auditService.scan(resolvedLabelCode, current.getBoxCode(), "EMPTY", false, msg + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, current.getStationCode(), current.getMaterialCode());
            throw new BusinessException(ErrorCode.STATE_CONFLICT, msg);
        }

        if (standby.getStatus() != BoxStatus.FULL_STANDBY) {
            current.setStatus(BoxStatus.ABNORMAL);
            current.setHealthStatus("STANDBY_NOT_FULL");
            current.setLockReason("备用盒不是满盒状态，禁止切换");
            current.setLockedAt(LocalDateTime.now());
            current.setLastError("备用盒状态=" + standby.getStatus());
            boxRepository.save(current);
            alertService.open("ERROR", "BOX_SWITCH", current.getPairCode(), "备用盒状态异常", "当前盒 " + current.getBoxCode() + " 扫空时，备用盒 " + standby.getBoxCode() + " 状态为 " + standby.getStatus());
            auditService.scan(resolvedLabelCode, current.getBoxCode(), "EMPTY", false, "备用盒不是满盒状态，触发异常锁定，原始扫码=" + req.scanCode, req.operator, req.deviceNo, current.getStationCode(), current.getMaterialCode());
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "备用盒不是满盒状态，请人工处理，系统已锁定当前盒");
        }

        current.setStatus(BoxStatus.EMPTY_WAITING_PULL);
        current.setCurrentQty(BigDecimal.ZERO);
        current.setLastScanAt(LocalDateTime.now());
        current.setHealthStatus("OK");
        current.setLockReason(null);
        current.setLastError(null);
        boxRepository.save(current);

        standby.setStatus(BoxStatus.IN_USE);
        standby.setLastScanAt(LocalDateTime.now());
        standby.setCycleCount((standby.getCycleCount() == null ? 0 : standby.getCycleCount()) + 1);
        standby.setHealthStatus("OK");
        standby.setLockReason(null);
        standby.setLastError(null);
        boxRepository.save(standby);

        ReplenishmentTaskEntity task = createTask(req, current);
        task.setMaterialImageUrl(label.getMaterialImageUrl());
        taskRepository.save(task);

        auditService.scan(resolvedLabelCode, current.getBoxCode(), "EMPTY", true, "扫码成功，生成补货任务：" + task.getTaskNo() + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, current.getStationCode(), current.getMaterialCode());
        auditService.task(task.getTaskNo(), "CREATE_BY_SCAN", null, task.getStatus().name(), req.operator, "由现场扫码生成，备用盒已切换为使用中");
        pushService.publish("tasks", task);
        pushService.publish("boxes", pair);

        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        r.taskCreated = true;
        r.taskNo = task.getTaskNo();
        r.message = "扫码成功，当前盒切换为空盒待补，备用盒切换为正在使用";
        r.scannedCode = req.scanCode;
        r.resolvedLabelCode = resolvedLabelCode;
        fillLabelFields(r, label);
        r.currentBoxCode = current.getBoxCode();
        r.currentBoxStatus = current.getStatus();
        r.standbyBoxCode = standby.getBoxCode();
        r.standbyBoxStatus = standby.getStatus();
        r.taskStatus = task.getStatus();
        r.priority = task.getPriority() == null ? null : task.getPriority().name();
        r.duplicateBlocked = false;
        r.warnings = List.of();
        r.scanAt = LocalDateTime.now();
        return r;
    }



    private ScanDtos.ScanResult doSpareUrgentPull(ScanDtos.ScanRequest req, LabelEntity label, BoxEntity scannedStandby, BoxEntity otherBox, List<BoxEntity> pair) {
        String resolvedLabelCode = label.getLabelCode();
        String emptyLabelCode = firstNonBlank(otherBox.getLabelCode(), resolvedLabelCode);
        List<ReplenishmentTaskEntity> existing = taskRepository.findBySourceLabelCodeAndStatusIn(emptyLabelCode, activeStatuses());
        if (!existing.isEmpty() && !Boolean.TRUE.equals(req.allowRepeat)) {
            ReplenishmentTaskEntity t = latest(existing);
            return duplicateResult(otherBox, t, "该AB盒已存在未完成紧急补货任务，系统已阻止重复生成：" + t.getTaskNo());
        }
        if (otherBox.getStatus() != BoxStatus.IN_USE) {
            String msg = "备用标签触发紧急拉动时，另一只盒必须处于正在使用状态，当前状态=" + otherBox.getStatus();
            scannedStandby.setHealthStatus("SPARE_PULL_BLOCKED");
            scannedStandby.setLastError(msg);
            boxRepository.save(scannedStandby);
            alertService.open("ERROR", "BOX_SWITCH", scannedStandby.getPairCode(), "备用标签紧急拉动被拦截", msg);
            auditService.scan(resolvedLabelCode, scannedStandby.getBoxCode(), "SPARE_URGENT", false, msg + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, scannedStandby.getStationCode(), scannedStandby.getMaterialCode());
            throw new BusinessException(ErrorCode.STATE_CONFLICT, msg);
        }
        otherBox.setStatus(BoxStatus.EMPTY_WAITING_PULL);
        otherBox.setCurrentQty(BigDecimal.ZERO);
        otherBox.setLastScanAt(LocalDateTime.now());
        otherBox.setHealthStatus("OK");
        otherBox.setLockReason(null);
        otherBox.setLastError(null);
        boxRepository.save(otherBox);
        scannedStandby.setStatus(BoxStatus.IN_USE);
        scannedStandby.setLastScanAt(LocalDateTime.now());
        scannedStandby.setCycleCount((scannedStandby.getCycleCount() == null ? 0 : scannedStandby.getCycleCount()) + 1);
        scannedStandby.setHealthStatus("URGENT_SPARE_USED");
        scannedStandby.setLastError(null);
        boxRepository.save(scannedStandby);

        ReplenishmentTaskEntity task = createTask(req, otherBox);
        task.setMaterialImageUrl(label.getMaterialImageUrl());
        task.setPriority(PriorityLevel.URGENT);
        task.setDeadlineAt(LocalDateTime.now().plusMinutes(Math.min(timeoutMinutes, 30)));
        task.setRemark("现场扫描备用标签，原使用盒已切为空盒待补；任务绑定空盒，避免补回正在使用的备用盒");
        taskRepository.save(task);
        auditService.scan(resolvedLabelCode, otherBox.getBoxCode(), "SPARE_URGENT", true, "扫描备用标签，生成紧急补货任务：" + task.getTaskNo(), req.operator, req.deviceNo, otherBox.getStationCode(), otherBox.getMaterialCode());
        auditService.task(task.getTaskNo(), "CREATE_BY_SPARE_LABEL", null, task.getStatus().name(), req.operator, task.getRemark());
        pushService.publish("tasks", task);
        pushService.publish("boxes", pair);

        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        r.taskCreated = true;
        r.taskNo = task.getTaskNo();
        r.message = "备用标签扫码成功，已给原使用盒生成紧急补货任务";
        r.scannedCode = req.scanCode;
        r.resolvedLabelCode = resolvedLabelCode;
        fillLabelFields(r, label);
        r.currentBoxCode = otherBox.getBoxCode();
        r.currentBoxStatus = otherBox.getStatus();
        r.standbyBoxCode = scannedStandby.getBoxCode();
        r.standbyBoxStatus = scannedStandby.getStatus();
        r.taskStatus = task.getStatus();
        r.priority = task.getPriority().name();
        r.duplicateBlocked = false;
        r.warnings = List.of("该任务来自备用标签，优先级为紧急", "仓库大屏会进入紧急区域");
        r.scanAt = LocalDateTime.now();
        return r;
    }

    /**
     * V0.6 真实标签默认不含 A/B 盒别。未绑定盒子的标签扫码后直接生成补货任务，
     * 不再强行进入双盒轮换，避免因为现场标签格式变化导致系统卡死。
     */
    private ScanDtos.ScanResult doDirectPull(ScanDtos.ScanRequest req, LabelEntity label) {
        String resolvedLabelCode = label.getLabelCode();
        List<TaskStatus> active = activeStatuses();
        List<ReplenishmentTaskEntity> existing = taskRepository.findBySourceLabelCodeAndStatusIn(resolvedLabelCode, active);
        if (!existing.isEmpty() && !Boolean.TRUE.equals(req.allowRepeat)) {
            ReplenishmentTaskEntity t = existing.stream().max(Comparator.comparing(ReplenishmentTaskEntity::getCreatedAt)).orElse(existing.get(0));
            auditService.scan(resolvedLabelCode, null, "DIRECT_PULL_DUPLICATE", true, "真实标签重复扫码被拦截，返回已有任务：" + t.getTaskNo() + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, firstNonBlank(label.getSendStationAddress(), label.getDeliveryAddress(), label.getStationCode()), label.getMaterialCode());
            return duplicateResult(label, t, "该真实标签已经存在未完成补货任务，系统已阻止重复生成：" + t.getTaskNo());
        }

        ReplenishmentTaskEntity task = createTaskFromLabel(req, label);
        if ("URGENT".equalsIgnoreCase(firstNonBlank(req.action, "")) || "SPARE".equalsIgnoreCase(firstNonBlank(label.getLabelUsageType(), label.getBoxSide(), "")) || "URGENT".equalsIgnoreCase(firstNonBlank(label.getDeliveryMode(), ""))) {
            task.setPriority(PriorityLevel.URGENT);
            task.setDeliveryMode("URGENT");
            task.setDeadlineAt(LocalDateTime.now().plusMinutes(Math.min(timeoutMinutes, 30)));
            task.setRemark("按备用标签生成紧急配送任务");
        }
        taskRepository.save(task);

        auditService.scan(resolvedLabelCode, null, "DIRECT_PULL", true, "真实工厂标签扫码成功，生成补货任务：" + task.getTaskNo() + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, firstNonBlank(label.getSendStationAddress(), label.getDeliveryAddress(), label.getStationCode()), label.getMaterialCode());
        auditService.task(task.getTaskNo(), "CREATE_BY_FACTORY_LABEL", null, task.getStatus().name(), req.operator, "由真实工厂标签直接扫码生成，不执行 A/B 双盒切换");
        pushService.publish("tasks", task);

        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        fillLabelFields(r, label);
        r.taskCreated = true;
        r.taskNo = task.getTaskNo();
        r.message = "扫码成功，已按真实工厂标签生成补货任务；该标签未绑定 A/B 双盒，因此不执行盒子轮换";
        r.scannedCode = req.scanCode;
        r.resolvedLabelCode = resolvedLabelCode;
        r.taskStatus = task.getStatus();
        r.priority = task.getPriority() == null ? null : task.getPriority().name();
        r.duplicateBlocked = false;
        r.warnings = List.of("当前标签按仓库代码直接拉动", "未绑定 A/B 双盒，不切换盒子状态");
        r.scanAt = LocalDateTime.now();
        return r;
    }

    private ReplenishmentTaskEntity createTaskFromLabel(ScanDtos.ScanRequest req, LabelEntity label) {
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setTaskNo(IdGenerator.id("RP"));
        task.setSourceLabelCode(label.getLabelCode());
        task.setBarcodeValue(firstNonBlank(label.getBarcodeValue(), label.getPrimaryScanValue()));
        task.setWarehouseCode(label.getWarehouseCode());
        task.setWarehouseAddress(label.getWarehouseAddress());
        task.setSendStationAddress(label.getSendStationAddress());
        task.setBoxSize(label.getBoxSize());
        task.setDelivererEmployeeNo(label.getDelivererEmployeeNo());
        task.setKanbanCardNo(label.getKanbanCardNo());
        task.setIdempotencyKey(req.idempotencyKey);
        task.setBoxCode(label.getBoxCode());
        task.setPairCode(label.getPairCode());
        task.setBoxSide(label.getBoxSide());
        task.setLabelUsageType(firstNonBlank(label.getLabelUsageType(), "USE"));
        task.setDeliveryMode(firstNonBlank(label.getDeliveryMode(), "NORMAL"));
        task.setLineCode(label.getLineCode());
        task.setStationCode(firstNonBlank(label.getStationCode(), label.getSendStationAddress(), label.getDeliveryAddress()));
        task.setStationName(firstNonBlank(label.getStationName(), label.getSendStationAddress(), label.getDeliveryAddress()));
        task.setProjectCode(label.getProjectCode());
        task.setRouteName(label.getRouteName());
        task.setDeliveryAddress(firstNonBlank(label.getDeliveryAddress(), label.getSendStationAddress()));
        task.setWarehouseLocation(firstNonBlank(label.getWarehouseLocation(), label.getWarehouseAddress()));
        task.setMaterialCode(label.getMaterialCode());
        task.setMaterialName(label.getMaterialName());
        task.setWarehouseMaterialCode(label.getWarehouseMaterialCode());
        task.setMaterialImageUrl(label.getMaterialImageUrl());
        task.setRequestQty(label.getStandardQty());
        task.setStatus(TaskStatus.CREATED);
        task.setPriority(PriorityLevel.NORMAL);
        task.setCreatedBy(req.operator);
        task.setDeadlineAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        task.setLastActionAt(LocalDateTime.now());
        return task;
    }

    private ReplenishmentTaskEntity createTask(ScanDtos.ScanRequest req, BoxEntity box) {
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setTaskNo(IdGenerator.id("RP"));
        task.setSourceLabelCode(box.getLabelCode());
        task.setBarcodeValue(box.getBarcodeValue());
        task.setWarehouseCode(box.getWarehouseCode());
        task.setWarehouseAddress(box.getWarehouseAddress());
        task.setSendStationAddress(box.getSendStationAddress());
        task.setBoxSize(box.getBoxSize());
        task.setDelivererEmployeeNo(box.getDelivererEmployeeNo());
        task.setKanbanCardNo(box.getKanbanCardNo());
        task.setIdempotencyKey(req.idempotencyKey);
        task.setBoxCode(box.getBoxCode());
        task.setPairCode(box.getPairCode());
        task.setBoxSide(box.getBoxSide());
        task.setLabelUsageType("USE");
        task.setDeliveryMode("NORMAL");
        task.setLineCode(box.getLineCode());
        task.setStationCode(box.getStationCode());
        task.setStationName(box.getStationName());
        task.setProjectCode(box.getProjectCode());
        task.setRouteName(box.getRouteName());
        task.setDeliveryAddress(box.getDeliveryAddress());
        task.setWarehouseLocation(box.getWarehouseLocation());
        task.setMaterialCode(box.getMaterialCode());
        task.setMaterialName(box.getMaterialName());
        task.setWarehouseMaterialCode(box.getWarehouseMaterialCode());
        task.setRequestQty(box.getStandardQty());
        task.setStatus(TaskStatus.CREATED);
        task.setPriority(PriorityLevel.NORMAL);
        task.setCreatedBy(req.operator);
        task.setDeadlineAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        task.setLastActionAt(LocalDateTime.now());
        return task;
    }

    private ScanDtos.ScanResult duplicateResult(BoxEntity box, ReplenishmentTaskEntity task, String message) {
        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        r.taskCreated = false;
        r.taskNo = task.getTaskNo();
        r.message = message;
        r.scannedCode = box.getBarcodeValue();
        r.resolvedLabelCode = box.getLabelCode();
        r.primaryScanValue = firstNonBlank(box.getBarcodeValue(), box.getWarehouseCode());
        r.barcodeValue = box.getBarcodeValue();
        r.warehouseCode = box.getWarehouseCode();
        r.warehouseAddress = box.getWarehouseAddress();
        r.sendStationAddress = box.getSendStationAddress();
        r.boxSize = box.getBoxSize();
        r.delivererEmployeeNo = box.getDelivererEmployeeNo();
        r.kanbanCardNo = box.getKanbanCardNo();
        r.materialCode = box.getMaterialCode();
        r.materialName = box.getMaterialName();
        r.deliveryAddress = box.getDeliveryAddress();
        r.warehouseLocation = box.getWarehouseLocation();
        r.currentBoxCode = box.getBoxCode();
        r.currentBoxStatus = box.getStatus();
        r.taskStatus = task.getStatus();
        r.duplicateBlocked = true;
        r.warnings = List.of("重复扫码未生成新任务", "可在仓库任务页面继续处理原任务");
        r.scanAt = LocalDateTime.now();
        return r;
    }

    private ScanDtos.ScanResult duplicateResult(LabelEntity label, ReplenishmentTaskEntity task, String message) {
        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        fillLabelFields(r, label);
        r.taskCreated = false;
        r.taskNo = task.getTaskNo();
        r.message = message;
        r.scannedCode = label.getPrimaryScanValue();
        r.resolvedLabelCode = label.getLabelCode();
        r.taskStatus = task.getStatus();
        r.duplicateBlocked = true;
        r.warnings = List.of("重复扫码未生成新任务", "可在仓库任务页面继续处理原任务");
        r.scanAt = LocalDateTime.now();
        return r;
    }

    private void fillLabelFields(ScanDtos.ScanResult r, LabelEntity label) {
        r.labelType = label.getLabelType();
        r.codeCarrierType = label.getCodeCarrierType();
        r.primaryScanValue = label.getPrimaryScanValue();
        r.barcodeValue = label.getBarcodeValue();
        r.warehouseCode = label.getWarehouseCode();
        r.warehouseAddress = label.getWarehouseAddress();
        r.sendStationAddress = label.getSendStationAddress();
        r.boxSize = label.getBoxSize();
        r.delivererEmployeeNo = label.getDelivererEmployeeNo();
        r.kanbanCardNo = label.getKanbanCardNo();
        r.materialCode = label.getMaterialCode();
        r.materialName = label.getMaterialName();
        r.materialImageUrl = label.getMaterialImageUrl();
        r.deliveryAddress = firstNonBlank(label.getDeliveryAddress(), label.getSendStationAddress());
        r.warehouseLocation = firstNonBlank(label.getWarehouseLocation(), label.getWarehouseAddress());
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }

    private List<TaskStatus> activeStatuses() {
        return List.of(TaskStatus.CREATED, TaskStatus.ACCEPTED, TaskStatus.PICKING, TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED, TaskStatus.EXCEPTION);
    }
}
