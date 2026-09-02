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
    private final MaterialMappingRepository mappingRepository;
    private final StationMaterialRepository stationMaterialRepository;
    private final SystemConfigRepository configRepository;
    private final MaterialRepository materialRepository;
    private final DataScopeService dataScopeService;

    @Value("${app.task.timeout-minutes:120}")
    private long timeoutMinutes;

    /** 物料号扫码去重时间窗（分钟）配置键。后台“系统配置”页可改，扫码时实时读取，无需重启。 */
    private static final String DEDUP_WINDOW_KEY = "task.dedup.window-minutes";
    /** 默认去重时间窗：3 分钟。 */
    private static final long DEDUP_WINDOW_DEFAULT = 3;

    @Transactional
    public ScanDtos.ScanResult scanEmpty(ScanDtos.ScanRequest req) {
        if (req == null) req = new ScanDtos.ScanRequest();
        String rawScanCode = req.scanCode == null || req.scanCode.isBlank() ? req.labelCode : req.scanCode;
        rawScanCode = labelResolverService.normalize(rawScanCode);
        // 工位二维码内容形如 "物料号,工位,使用/备用"，拆出物料号/工位/用途。
        String[] parsed = splitMaterialAndStation(rawScanCode);
        rawScanCode = parsed[0];
        if (parsed[1] != null && firstNonBlank(req.stationCode) == null) req.stationCode = parsed[1];
        if (parsed[2] != null && firstNonBlank(req.usageType) == null) req.usageType = parsed[2];
        req.scanCode = rawScanCode;
        req.labelCode = rawScanCode;
        req.operator = OperatorResolver.currentOperator();
        req.deviceNo = (req.deviceNo == null || req.deviceNo.isBlank()) ? "UNKNOWN" : req.deviceNo.trim();
        String requestKey = firstNonBlank(req.idempotencyKey, RequestContext.getTraceId());
        String requestHash = RequestDigest.sha256(rawScanCode, req.operator, req.deviceNo, req.action, RequestDigest.valueOf(req.requestQty), firstNonBlank(req.requestUnit, "个"), RequestDigest.valueOf(req.allowRepeat));
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
        auditService.scan(firstNonBlank(task.getSourceLabelCode(), rawScanCode), task.getBoxCode(), "RECEIVE", true, "现场收货确认，任务=" + task.getTaskNo() + "，空盒=" + req.emptyContainerNo, req.operator, req.deviceNo, task.getStationCode(), task.getMaterialCode(), task.getFactory(), task.getDeliveryArea());
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
        auditService.scan(rawScanCode, task == null ? null : task.getBoxCode(), "SITE_EXCEPTION", true, event.getContent(), req.operator, req.deviceNo, task == null ? null : task.getStationCode(), task == null ? null : task.getMaterialCode(), task == null ? null : task.getFactory(), task == null ? null : task.getDeliveryArea());
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
        List<TaskStatus> statuses = receivableStatuses();
        Optional<ReplenishmentTaskEntity> byTask = taskRepository.findByTaskNo(scanCode);
        if (byTask.isPresent()) {
            ReplenishmentTaskEntity task = byTask.get();
            if (statuses.contains(task.getStatus())) return task.getTaskNo();
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "任务当前不可收货确认：" + task.getStatus());
        }
        try {
            LabelEntity label = labelResolverService.resolve(scanCode);
            List<ReplenishmentTaskEntity> byLabel = taskRepository.findBySourceLabelCodeAndStatusIn(label.getLabelCode(), statuses);
            if (!byLabel.isEmpty()) return uniqueReceivable(byLabel, scanCode);
            if (label.getBoxCode() != null) {
                List<ReplenishmentTaskEntity> byBox = taskRepository.findByBoxCodeAndStatusIn(label.getBoxCode(), statuses);
                if (!byBox.isEmpty()) return uniqueReceivable(byBox, scanCode);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException ignored) {}
        List<ReplenishmentTaskEntity> byWarehouseCode = taskRepository.findByWarehouseCodeAndStatusIn(scanCode, statuses);
        if (!byWarehouseCode.isEmpty()) return uniqueReceivable(byWarehouseCode, scanCode);
        return null;
    }

    private String findAnyActiveTaskNo(String scanCode) {
        if (scanCode == null || scanCode.isBlank()) return null;
        Optional<ReplenishmentTaskEntity> byTask = taskRepository.findByTaskNo(scanCode);
        if (byTask.isPresent()) return byTask.get().getTaskNo();
        List<ReplenishmentTaskEntity> byWarehouseCode = taskRepository.findByWarehouseCodeAndStatusIn(scanCode, activeStatuses());
        if (!byWarehouseCode.isEmpty()) return latest(byWarehouseCode).getTaskNo();
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
        LabelEntity label;
        try {
            label = labelResolverService.resolveForUpdate(req.scanCode);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.NOT_FOUND) return doMaterialCodePull(req);
            throw e;
        }

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

        MappingScope boxScope = resolveMappingScope(box.getWarehouseCode(), box.getMaterialCode());
        List<ReplenishmentTaskEntity> existing = taskRepository.findByFactoryAndDeliveryAreaAndSourceLabelCodeAndStatusIn(
                boxScope.factory(), boxScope.deliveryArea(), resolvedLabelCode, blockingStatuses());
        if (!existing.isEmpty() && !Boolean.TRUE.equals(req.allowRepeat)) {
            ReplenishmentTaskEntity t = existing.stream().max(Comparator.comparing(ReplenishmentTaskEntity::getCreatedAt)).orElse(existing.get(0));
            auditService.scan(resolvedLabelCode, box.getBoxCode(), "EMPTY_DUPLICATE", true, "重复扫码被拦截，返回已有任务：" + t.getTaskNo() + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, box.getStationCode(), box.getMaterialCode(), t.getFactory(), t.getDeliveryArea());
            return duplicateResult(box, t, "该标签已经存在仓库未出发的补货任务，系统已阻止重复生成：" + t.getTaskNo());
        }

        List<BoxEntity> pair = boxRepository.findByPairCodeForUpdate(box.getPairCode());
        if (pair.size() != 2) {
            box.setStatus(BoxStatus.ABNORMAL);
            box.setHealthStatus("PAIR_BROKEN");
            box.setLockReason("AB配对数量不是2");
            box.setLockedAt(LocalDateTime.now());
            boxRepository.save(box);
            alertService.open("ERROR", "BOX_PAIR", box.getPairCode(), "AB双盒配对异常", "配对编码 " + box.getPairCode() + " 下盒子数量为 " + pair.size());
            auditService.scan(resolvedLabelCode, box.getBoxCode(), "EMPTY", false, "AB配对异常，原始扫码=" + req.scanCode, req.operator, req.deviceNo, box.getStationCode(), box.getMaterialCode(), boxScope.factory(), boxScope.deliveryArea());
            throw new BusinessException(ErrorCode.DATA_DIRTY, "AB配对异常，请维护盒子数据：" + box.getPairCode());
        }

        BoxEntity current = pair.stream().filter(x -> Objects.equals(x.getBoxCode(), box.getBoxCode())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_DIRTY, "当前盒不在AB配对中"));
        BoxEntity standby = pair.stream().filter(x -> !Objects.equals(x.getBoxCode(), box.getBoxCode())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_DIRTY, "未找到AB备用盒"));

        if (current.getStatus() == BoxStatus.FULL_STANDBY) {
            return doSpareUrgentPull(req, label, current, standby, pair);
        }

        if (current.getStatus() != BoxStatus.IN_USE) {
            String msg = "当前扫码盒不是正在使用状态，当前状态：" + current.getStatus();
            current.setLastError(msg);
            current.setHealthStatus("STATE_CONFLICT");
            boxRepository.save(current);
            auditService.scan(resolvedLabelCode, current.getBoxCode(), "EMPTY", false, msg + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, current.getStationCode(), current.getMaterialCode(), boxScope.factory(), boxScope.deliveryArea());
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
            auditService.scan(resolvedLabelCode, current.getBoxCode(), "EMPTY", false, "备用盒不是满盒状态，触发异常锁定，原始扫码=" + req.scanCode, req.operator, req.deviceNo, current.getStationCode(), current.getMaterialCode(), boxScope.factory(), boxScope.deliveryArea());
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

        auditService.scan(resolvedLabelCode, current.getBoxCode(), "EMPTY", true, "扫码成功，生成补货任务：" + task.getTaskNo() + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, current.getStationCode(), current.getMaterialCode(), task.getFactory(), task.getDeliveryArea());
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
        r.requestQty = task.getRequestQty();
        r.requestUnit = firstNonBlank(task.getRequestUnit(), "个");
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
        MappingScope boxScope = resolveMappingScope(otherBox.getWarehouseCode(), otherBox.getMaterialCode());
        List<ReplenishmentTaskEntity> existing = taskRepository.findByFactoryAndDeliveryAreaAndSourceLabelCodeAndStatusIn(
                boxScope.factory(), boxScope.deliveryArea(), emptyLabelCode, blockingStatuses());
        if (!existing.isEmpty() && !Boolean.TRUE.equals(req.allowRepeat)) {
            ReplenishmentTaskEntity t = latest(existing);
            return duplicateResult(otherBox, t, "该AB盒已存在仓库未出发的紧急补货任务，系统已阻止重复生成：" + t.getTaskNo());
        }
        if (otherBox.getStatus() != BoxStatus.IN_USE) {
            String msg = "备用标签触发紧急拉动时，另一只盒必须处于正在使用状态，当前状态=" + otherBox.getStatus();
            scannedStandby.setHealthStatus("SPARE_PULL_BLOCKED");
            scannedStandby.setLastError(msg);
            boxRepository.save(scannedStandby);
            alertService.open("ERROR", "BOX_SWITCH", scannedStandby.getPairCode(), "备用标签紧急拉动被拦截", msg);
            auditService.scan(resolvedLabelCode, scannedStandby.getBoxCode(), "SPARE_URGENT", false, msg + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, scannedStandby.getStationCode(), scannedStandby.getMaterialCode(), boxScope.factory(), boxScope.deliveryArea());
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
        auditService.scan(resolvedLabelCode, otherBox.getBoxCode(), "SPARE_URGENT", true, "扫描备用标签，生成紧急补货任务：" + task.getTaskNo(), req.operator, req.deviceNo, otherBox.getStationCode(), otherBox.getMaterialCode(), task.getFactory(), task.getDeliveryArea());
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
        r.requestQty = task.getRequestQty();
        r.requestUnit = firstNonBlank(task.getRequestUnit(), "个");
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
        MappingScope labelScope = resolveMappingScope(label.getWarehouseCode(), label.getMaterialCode());
        List<ReplenishmentTaskEntity> existing = taskRepository.findByFactoryAndDeliveryAreaAndSourceLabelCodeAndStatusIn(
                labelScope.factory(), labelScope.deliveryArea(), resolvedLabelCode, blockingStatuses());
        if (!existing.isEmpty() && !Boolean.TRUE.equals(req.allowRepeat)) {
            ReplenishmentTaskEntity t = existing.stream().max(Comparator.comparing(ReplenishmentTaskEntity::getCreatedAt)).orElse(existing.get(0));
            auditService.scan(resolvedLabelCode, null, "DIRECT_PULL_DUPLICATE", true, "真实标签重复扫码被拦截，返回已有任务：" + t.getTaskNo() + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, firstNonBlank(label.getSendStationAddress(), label.getDeliveryAddress(), label.getStationCode()), label.getMaterialCode(), t.getFactory(), t.getDeliveryArea());
            return duplicateResult(label, t, "该真实标签已经存在仓库未出发的补货任务，系统已阻止重复生成：" + t.getTaskNo());
        }

        ReplenishmentTaskEntity task = createTaskFromLabel(req, label);
        if ("URGENT".equalsIgnoreCase(firstNonBlank(req.action, "")) || "SPARE".equalsIgnoreCase(firstNonBlank(label.getLabelUsageType(), label.getBoxSide(), "")) || "URGENT".equalsIgnoreCase(firstNonBlank(label.getDeliveryMode(), ""))) {
            task.setPriority(PriorityLevel.URGENT);
            task.setDeliveryMode("URGENT");
            task.setDeadlineAt(LocalDateTime.now().plusMinutes(Math.min(timeoutMinutes, 30)));
            task.setRemark("按备用标签生成紧急配送任务");
        }
        taskRepository.save(task);

        auditService.scan(resolvedLabelCode, null, "DIRECT_PULL", true, "真实工厂标签扫码成功，生成补货任务：" + task.getTaskNo() + "，原始扫码=" + req.scanCode, req.operator, req.deviceNo, firstNonBlank(label.getSendStationAddress(), label.getDeliveryAddress(), label.getStationCode()), label.getMaterialCode(), task.getFactory(), task.getDeliveryArea());
        auditService.task(task.getTaskNo(), "CREATE_BY_FACTORY_LABEL", null, task.getStatus().name(), req.operator, "由真实工厂标签直接扫码生成，不执行 A/B 双盒切换");
        pushService.publish("tasks", task);

        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        fillLabelFields(r, label);
        r.taskCreated = true;
        r.taskNo = task.getTaskNo();
        r.message = "扫码成功，已按真实工厂标签生成补货任务；该标签未绑定 A/B 双盒，因此不执行盒子轮换";
        r.scannedCode = req.scanCode;
        r.resolvedLabelCode = resolvedLabelCode;
        r.requestQty = task.getRequestQty();
        r.requestUnit = firstNonBlank(task.getRequestUnit(), "个");
        r.taskStatus = task.getStatus();
        r.priority = task.getPriority() == null ? null : task.getPriority().name();
        r.duplicateBlocked = false;
        r.warnings = List.of("当前标签按仓库代码直接拉动", "未绑定 A/B 双盒，不切换盒子状态");
        r.scanAt = LocalDateTime.now();
        return r;
    }

    private ScanDtos.ScanResult doMaterialCodePull(ScanDtos.ScanRequest req) {
        String materialCode = guard.notBlank(req.scanCode, "物料号");
        String stationCode = firstNonBlank(req.stationCode);
        boolean spare = "SPARE".equalsIgnoreCase(firstNonBlank(req.usageType, "")) || "URGENT".equalsIgnoreCase(firstNonBlank(req.action, ""));

        // 工位二维码 = 物料号 + 工位 + 使用/备用。
        // 物料号 → 查料号映射核对物料、取仓库代号；工位号不参与映射，直接透传到仓库标签的发送工位地址；使用→正常、备用→紧急。
        MaterialMappingEntity mapping = chooseMapping(materialCode, stationCode, spare);
        Optional<StationMaterialEntity> station = resolveStation(materialCode, stationCode);

        requireMappingScope(mapping);
        List<ReplenishmentTaskEntity> existing = taskRepository.findByFactoryAndDeliveryAreaAndWarehouseCodeAndStatusIn(
                mapping.getFactory().trim(), mapping.getDeliveryArea().trim(), mapping.getWarehouseCode(), blockingStatuses());
        // 时间窗去重：只拦最近 N 分钟内创建的同仓库代号任务（挡住手抖/设备重发）；
        // 超过时间窗的旧任务视为“上一轮用料”，同一个盒子再次用空可正常生成新任务。
        long windowMinutes = dedupWindowMinutes();
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(windowMinutes);
        List<ReplenishmentTaskEntity> recent = existing.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(windowStart))
                .toList();
        if (!recent.isEmpty() && !Boolean.TRUE.equals(req.allowRepeat)) {
            ReplenishmentTaskEntity old = latest(recent);
            auditService.scan(materialCode, null, "MATERIAL_PULL_DUPLICATE", true, "仓库代号" + mapping.getWarehouseCode() + "在" + windowMinutes + "分钟内已有补货任务，拦截重复：" + old.getTaskNo(), req.operator, req.deviceNo, firstNonBlank(old.getSendStationAddress(), old.getDeliveryAddress(), old.getStationCode()), materialCode, old.getFactory(), old.getDeliveryArea());
            return duplicateMaterialResult(old, materialCode, "该仓库代号在" + windowMinutes + "分钟内已生成补货任务，系统已阻止重复申请：" + old.getTaskNo() + "（如确需再次申请，请使用强制申请）");
        }

        ReplenishmentTaskEntity task = createTaskFromMapping(req, mapping, station.orElse(null), stationCode, spare);
        taskRepository.save(task);
        auditService.scan(materialCode, null, spare ? "MATERIAL_PULL_URGENT" : "MATERIAL_PULL", true, "现场扫码成功，生成" + (spare ? "紧急" : "正常") + "补货任务：" + task.getTaskNo() + "，仓库代号=" + task.getWarehouseCode(), req.operator, req.deviceNo, task.getSendStationAddress(), task.getMaterialCode(), task.getFactory(), task.getDeliveryArea());
        auditService.task(task.getTaskNo(), spare ? "CREATE_BY_MATERIAL_URGENT" : "CREATE_BY_MATERIAL", null, task.getStatus().name(), req.operator, "由工位二维码(物料号+工位+" + (spare ? "备用" : "使用") + ")生成");
        pushService.publish("tasks", task);

        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        r.taskCreated = true;
        r.taskNo = task.getTaskNo();
        r.message = spare
                ? "扫码成功（备用盒），已生成紧急配送任务，仓库大屏进入紧急区域"
                : "扫码成功（使用盒），已生成正常配送任务";
        r.scannedCode = materialCode;
        r.resolvedLabelCode = materialCode;
        r.primaryScanValue = materialCode;
        r.barcodeValue = task.getWarehouseCode();
        r.warehouseCode = task.getWarehouseCode();
        r.warehouseAddress = task.getWarehouseAddress();
        r.sendStationAddress = task.getSendStationAddress();
        r.boxSize = task.getBoxSize();
        r.requestQty = task.getRequestQty();
        r.requestUnit = firstNonBlank(task.getRequestUnit(), "个");
        r.materialCode = task.getMaterialCode();
        r.materialName = task.getMaterialName();
        r.deliveryAddress = task.getDeliveryAddress();
        r.warehouseLocation = task.getWarehouseLocation();
        r.taskStatus = task.getStatus();
        r.priority = task.getPriority() == null ? null : task.getPriority().name();
        r.duplicateBlocked = false;
        r.warnings = List.of("仓库标签条形码将打印仓库代号：" + task.getWarehouseCode(), spare ? "用途：备用→紧急配送" : "用途：使用→正常配送");
        r.scanAt = LocalDateTime.now();
        return r;
    }

    /**
     * 双条件匹配：选仓库代号需同时满足
     *   条件1：二维码料号 = Excel(料号映射)中的物料号(lineMaterialCode)。
     *   条件2：二维码发送工位地址 = Excel(料号映射)中的总装地址(deliveryAddress)。
     * 两个条件都满足才算匹配成功；之后再按用途(使用/备用→NORMAL/URGENT)在结果中择一。
     * 兼容二维码只有物料号、未扫出工位地址的情况：此时退回仅条件1匹配。
     */
    MaterialMappingEntity chooseMapping(String materialCode, String stationAddress, boolean spare) {
        String deliveryType = spare ? "URGENT" : "NORMAL";
        // 条件1：物料号匹配，取该物料的全部映射候选。
        List<MaterialMappingEntity> candidates = mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc(materialCode);
        if (candidates.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "未找到物料号对应的料号映射：" + materialCode + "，请在基础数据→料号映射维护该物料的仓库代号");

        // 条件2：发送工位地址 = Excel 总装地址(deliveryAddress)。
        String target = firstNonBlank(stationAddress);
        if (target != null) {
            List<MaterialMappingEntity> matched = candidates.stream()
                    .filter(m -> normalizedEquals(m.getDeliveryAddress(), target))
                    .toList();
            if (matched.isEmpty()) {
                // 带上该物料已维护的地址清单，便于现场直接对照是二维码印错还是映射缺记录。
                String known = candidates.stream()
                        .map(MaterialMappingEntity::getDeliveryAddress)
                        .filter(a -> a != null && !a.isBlank())
                        .distinct()
                        .collect(java.util.stream.Collectors.joining("、"));
                throw new BusinessException(ErrorCode.NOT_FOUND,
                        "物料号 " + materialCode + " 没有总装地址为 " + target + " 的料号映射。"
                        + "该物料已维护的总装地址：" + (known.isEmpty() ? "无" : known)
                        + "。请核对工位二维码，或在基础数据→料号映射补充该工位的记录。");
            }
            candidates = matched;
        } else {
            // 没扫出工位地址时不许猜：同一物料常被多个工位共用（现场有物料对应 6 个总装地址），
            // 任取第一条会生成别的工位的仓库代号，标签直接印错、料也拉错。
            // 只有该物料全部映射同属一个总装地址时，缺工位才是安全的。
            long distinctAddresses = candidates.stream()
                    .map(m -> normalizeAddress(m.getDeliveryAddress()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            if (distinctAddresses > 1) {
                String addresses = candidates.stream()
                        .map(MaterialMappingEntity::getDeliveryAddress)
                        .filter(a -> a != null && !a.isBlank())
                        .distinct()
                        .collect(java.util.stream.Collectors.joining("、"));
                throw new BusinessException(ErrorCode.DATA_DIRTY,
                        "物料号 " + materialCode + " 对应多个总装地址（" + addresses + "），本次扫码未识别出工位地址，"
                        + "无法确定该送哪个工位，已拒绝建单。请使用带工位信息的工位二维码（物料号,工位,使用/备用），或在 APP 中选择工位后重试。");
            }
        }

        // 两个条件已满足，再按用途择一：优先取用途匹配的记录。
        List<MaterialMappingEntity> typed = candidates.stream()
                .filter(m -> deliveryType.equalsIgnoreCase(m.getDeliveryType()))
                .toList();
        if (typed.size() == 1) return typed.get(0);
        if (typed.size() > 1) {
            throw new BusinessException(ErrorCode.DATA_DIRTY,
                    "物料号 " + materialCode + "、工位 " + firstNonBlank(stationAddress, "未提供")
                            + "、用途 " + deliveryType + " 对应多条料号映射，无法唯一确定归属，已拒绝建单");
        }
        if (candidates.size() == 1) return candidates.get(0);
        throw new BusinessException(ErrorCode.DATA_DIRTY,
                "物料号 " + materialCode + "、工位 " + firstNonBlank(stationAddress, "未提供")
                        + " 对应多条料号映射且没有唯一的 " + deliveryType + " 用途记录，已拒绝建单");
    }

    private Optional<StationMaterialEntity> resolveStation(String materialCode, String stationCode) {
        if (stationCode != null) {
            Optional<StationMaterialEntity> exact = stationMaterialRepository.findByStationCodeAndMaterialCodeAndEnabledTrue(stationCode, materialCode);
            if (exact.isPresent()) return exact;
        }
        return stationMaterialRepository.findFirstByMaterialCodeAndEnabledTrue(materialCode);
    }

    private ReplenishmentTaskEntity createTaskFromMapping(ScanDtos.ScanRequest req, MaterialMappingEntity mapping, StationMaterialEntity station, String scannedStationCode, boolean spare) {
        requireMappingScope(mapping);
        dataScopeService.requireAccessFactoryArea(mapping.getFactory(), mapping.getDeliveryArea());
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setTaskNo(IdGenerator.idMinute("RP"));
        task.setFactory(mapping.getFactory());
        task.setSourceLabelCode(mapping.getLineMaterialCode());
        task.setBarcodeValue(mapping.getWarehouseCode());
        task.setWarehouseCode(mapping.getWarehouseCode());
        task.setBoxSize(mapping.getBoxSize());
        task.setIdempotencyKey(req.idempotencyKey);
        task.setLabelUsageType(spare ? "SPARE" : "USE");
        task.setDeliveryMode(spare ? "URGENT" : "NORMAL");
        task.setMaterialCode(mapping.getLineMaterialCode());
        task.setMaterialName(mapping.getLineMaterialCode());
        task.setWarehouseMaterialCode(firstNonBlank(mapping.getWarehouseMaterialCode(), mapping.getWarehouseCode()));
        task.setRequestQty(resolveRequestQty(req, mapping.getQuantity()));
        task.setRequestUnit(resolveRequestUnit(req));
        task.setDeliveryArea(mapping.getDeliveryArea().trim());
        task.setStatus(TaskStatus.CREATED);
        task.setPriority(spare ? PriorityLevel.URGENT : PriorityLevel.NORMAL);
        task.setCreatedBy(req.operator);
        task.setDeadlineAt(LocalDateTime.now().plusMinutes(spare ? Math.min(timeoutMinutes, 30) : timeoutMinutes));
        task.setLastActionAt(LocalDateTime.now());
        if (spare) task.setRemark("扫描备用盒标签，按紧急配送处理");
        // 工位地址：直接用工位二维码扫出的工位号透传；若没扫到再退回工位用料数据。
        String stationAddress = firstNonBlank(scannedStationCode, mapping.getStationCode());
        // 仓库位置和总装地址优先取料号映射中的直接配置，再退回工位用料数据。
        String mappingWarehouseLocation = mapping.getWarehouseLocation();
        String mappingDeliveryAddress = mapping.getDeliveryAddress();
        if (station != null) {
            task.setLineCode(station.getLineCode());
            task.setStationCode(firstNonBlank(stationAddress, station.getStationCode()));
            task.setStationName(station.getStationName());
            task.setProjectCode(station.getProjectCode());
            task.setRouteName(station.getRouteName());
            String resolvedDelivery = firstNonBlank(mappingDeliveryAddress, stationAddress, station.getDeliveryAddress(), station.getStationName(), station.getStationCode());
            task.setDeliveryAddress(resolvedDelivery);
            task.setSendStationAddress(resolvedDelivery);
            task.setWarehouseLocation(firstNonBlank(mappingWarehouseLocation, station.getWarehouseLocation()));
            task.setWarehouseAddress(firstNonBlank(mappingWarehouseLocation, station.getWarehouseLocation()));
            task.setMaterialName(firstNonBlank(station.getMaterialName(), mapping.getLineMaterialCode()));
        } else {
            String resolvedDelivery = firstNonBlank(mappingDeliveryAddress, stationAddress);
            task.setStationCode(firstNonBlank(stationAddress, mappingDeliveryAddress));
            task.setSendStationAddress(resolvedDelivery);
            task.setDeliveryAddress(resolvedDelivery);
            task.setWarehouseLocation(mappingWarehouseLocation);
            task.setWarehouseAddress(mappingWarehouseLocation);
        }
        return task;
    }

    private ScanDtos.ScanResult duplicateMaterialResult(ReplenishmentTaskEntity task, String materialCode, String message) {
        ScanDtos.ScanResult r = new ScanDtos.ScanResult();
        r.taskCreated = false;
        r.taskNo = task.getTaskNo();
        r.message = message;
        r.scannedCode = materialCode;
        r.resolvedLabelCode = task.getSourceLabelCode();
        r.primaryScanValue = materialCode;
        r.barcodeValue = task.getWarehouseCode();
        r.warehouseCode = task.getWarehouseCode();
        r.warehouseAddress = task.getWarehouseAddress();
        r.sendStationAddress = task.getSendStationAddress();
        r.boxSize = task.getBoxSize();
        r.requestQty = task.getRequestQty();
        r.requestUnit = firstNonBlank(task.getRequestUnit(), "个");
        r.materialCode = task.getMaterialCode();
        r.materialName = task.getMaterialName();
        r.deliveryAddress = task.getDeliveryAddress();
        r.warehouseLocation = task.getWarehouseLocation();
        r.taskStatus = task.getStatus();
        r.priority = task.getPriority() == null ? null : task.getPriority().name();
        r.duplicateBlocked = true;
        r.warnings = List.of("重复扫码未生成新任务", "可在仓库任务页面继续处理原任务");
        r.scanAt = LocalDateTime.now();
        return r;
    }

    private ReplenishmentTaskEntity createTaskFromLabel(ScanDtos.ScanRequest req, LabelEntity label) {
        MappingScope scope = snapshotOrMappingScope(label.getFactory(), label.getDeliveryArea(), label.getWarehouseCode(), label.getMaterialCode());
        dataScopeService.requireAccessFactoryArea(scope.factory(), scope.deliveryArea());
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setTaskNo(IdGenerator.idMinute("RP"));
        task.setFactory(scope.factory());
        task.setDeliveryArea(scope.deliveryArea());
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
        task.setRequestQty(resolveRequestQty(req, label.getStandardQty()));
        task.setRequestUnit(resolveRequestUnit(req));
        task.setStatus(TaskStatus.CREATED);
        task.setPriority(PriorityLevel.NORMAL);
        task.setCreatedBy(req.operator);
        task.setDeadlineAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        task.setLastActionAt(LocalDateTime.now());
        return task;
    }

    private ReplenishmentTaskEntity createTask(ScanDtos.ScanRequest req, BoxEntity box) {
        MappingScope scope = snapshotOrMappingScope(box.getFactory(), box.getDeliveryArea(), box.getWarehouseCode(), box.getMaterialCode());
        dataScopeService.requireAccessFactoryArea(scope.factory(), scope.deliveryArea());
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setTaskNo(IdGenerator.idMinute("RP"));
        task.setFactory(scope.factory());
        task.setDeliveryArea(scope.deliveryArea());
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
        task.setRequestQty(resolveRequestQty(req, box.getStandardQty()));
        task.setRequestUnit(resolveRequestUnit(req));
        task.setStatus(TaskStatus.CREATED);
        task.setPriority(PriorityLevel.NORMAL);
        task.setCreatedBy(req.operator);
        task.setDeadlineAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        task.setLastActionAt(LocalDateTime.now());
        return task;
    }

    MappingScope resolveMappingScope(String warehouseCode, String materialCode) {
        String warehouse = firstNonBlank(warehouseCode);
        List<MaterialMappingEntity> candidates = List.of();
        if (warehouse != null) {
            candidates = mappingRepository.findByWarehouseCodeInAndEnabledTrue(List.of(warehouse));
        }
        if (candidates.isEmpty()) {
            String material = firstNonBlank(materialCode);
            if (material != null) {
                candidates = mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc(material);
            }
        }
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_DIRTY,
                    "无法从料号映射确定工厂和配送区域：warehouseCode=" + warehouseCode + "，materialCode=" + materialCode);
        }
        List<MappingScope> scopes = candidates.stream()
                .map(this::mappingScope)
                .distinct()
                .toList();
        if (scopes.size() != 1) {
            throw new BusinessException(ErrorCode.DATA_DIRTY,
                    "料号映射存在多个工厂或配送区域候选，无法唯一确定归属：warehouseCode=" + warehouseCode
                            + "，materialCode=" + materialCode);
        }
        return scopes.get(0);
    }

    MappingScope snapshotOrMappingScope(String factory, String deliveryArea, String warehouseCode, String materialCode) {
        String snapshotFactory = firstNonBlank(factory);
        String snapshotArea = firstNonBlank(deliveryArea);
        if (snapshotFactory != null && snapshotArea != null) return new MappingScope(snapshotFactory, snapshotArea);
        return resolveMappingScope(warehouseCode, materialCode);
    }

    private void requireMappingScope(MaterialMappingEntity mapping) {
        mappingScope(mapping);
    }

    private MappingScope mappingScope(MaterialMappingEntity mapping) {
        String factory = firstNonBlank(mapping.getFactory());
        String deliveryArea = firstNonBlank(mapping.getDeliveryArea());
        if (factory == null || deliveryArea == null) {
            throw new BusinessException(ErrorCode.DATA_DIRTY,
                    "料号映射缺少工厂或配送区域，禁止创建任务：mappingId=" + mapping.getId());
        }
        return new MappingScope(factory, deliveryArea);
    }

    record MappingScope(String factory, String deliveryArea) {}

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

    private boolean normalizedEquals(String left, String right) {
        String l = normalizeAddress(left);
        String r = normalizeAddress(right);
        return l != null && r != null && l.equals(r);
    }

    /**
     * 工位/总装地址归一化后再比较，避免"看起来一样却匹配不上"导致误报条件2失败。
     * 现场地址里有 76 条含波浪号，全角 ～ 与半角 ~ 混用；另兼容全角连字符、空格与大小写差异。
     * 只做字符形态归一，不改变地址语义（不删分隔符，避免 A-01 与 A0-1 被判为同一个）。
     */
    private String normalizeAddress(String value) {
        String v = firstNonBlank(value);
        if (v == null) return null;
        String s = v.replace('～', '~')
                    .replace('－', '-')
                    .replace('　', ' ')
                    .replace('（', '(')
                    .replace('）', ')');
        s = s.replaceAll("\\s+", "");
        return s.toUpperCase();
    }

    /**
     * 拆解工位二维码内容。格式为 "物料号,工位,使用/备用"（逗号分隔），例如 13799816,物料架-01-F03,备用。
     * 兼容只有物料号、或 "物料号,工位" 两段的情况；分隔符兼容中文逗号、|、;、/。
     * 返回 [物料号, 工位(可为null), 用途USE/SPARE(可为null)]。键值对/JSON 由 LabelResolverService 负责，这里跳过。
     */
    private String[] splitMaterialAndStation(String code) {
        if (code == null) return new String[]{null, null, null};
        String s = code.trim();
        if (s.startsWith("{") || s.contains("=")) return new String[]{s, null, null};
        // 工位地址本身带 '-'，故不能用 '-' 分隔；兼容逗号/竖线/分号以及二维码常见的多行版式：
        //   13609637(备用)\n物料架-11-E06
        String[] parts = s.split("[,，|;\\r\\n]+", -1);
        String first = parts.length > 0 ? parts[0].trim() : s;
        String usage = null;
        java.util.regex.Matcher usageSuffix = java.util.regex.Pattern
                .compile("^(.+?)[（(]\\s*(使用|备用|正常|紧急|USE|SPARE|NORMAL|URGENT)\\s*[）)]$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(first);
        String material;
        if (usageSuffix.matches()) {
            material = usageSuffix.group(1).trim();
            usage = normalizeUsage(usageSuffix.group(2));
        } else {
            material = first;
        }
        String station = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null;
        if (parts.length > 2 && !parts[2].isBlank()) usage = normalizeUsage(parts[2].trim());
        return new String[]{material, station, usage};
    }

    /** 把二维码里的 "使用"/"备用" 文案标准化为 USE/SPARE。 */
    private String normalizeUsage(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isBlank()) return null;
        if (v.contains("备") || v.equalsIgnoreCase("SPARE") || v.equalsIgnoreCase("URGENT") || v.contains("急")) return "SPARE";
        if (v.contains("使用") || v.contains("正常") || v.equalsIgnoreCase("USE") || v.equalsIgnoreCase("NORMAL")) return "USE";
        return null;
    }

    private List<TaskStatus> activeStatuses() {
        return List.of(TaskStatus.CREATED, TaskStatus.ACCEPTED, TaskStatus.PICKING, TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED, TaskStatus.EXCEPTION);
    }

    private List<TaskStatus> receivableStatuses() {
        return List.of(TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED);
    }

    private String uniqueReceivable(List<ReplenishmentTaskEntity> tasks, String scanCode) {
        if (tasks == null || tasks.isEmpty()) return null;
        if (tasks.size() == 1) return tasks.get(0).getTaskNo();
        List<ReplenishmentTaskEntity> sorted = tasks.stream()
                .sorted(Comparator.comparing(ReplenishmentTaskEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        String taskNos = sorted.stream().map(ReplenishmentTaskEntity::getTaskNo).filter(Objects::nonNull).limit(5).reduce((a, b) -> a + "," + b).orElse("");
        throw new BusinessException(ErrorCode.STATE_CONFLICT, "扫码值匹配到多个可收货任务，请扫描任务号或更精确的标签/盒号。扫码=" + scanCode + "，任务=" + taskNos);
    }

    private List<TaskStatus> blockingStatuses() {
        return activeStatuses();
    }

    /**
     * 读取去重时间窗（分钟）。优先读 sys_config 的 task.dedup.window-minutes，
     * 每次扫码实时读取，后台改完立即生效；缺失或非法时回退默认值 3 分钟。
     */
    private long dedupWindowMinutes() {
        try {
            return configRepository.findByConfigKey(DEDUP_WINDOW_KEY)
                    .map(SystemConfigEntity::getConfigValue)
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> {
                        try {
                            long minutes = Long.parseLong(v.trim());
                            return minutes < 0 ? DEDUP_WINDOW_DEFAULT : minutes;
                        } catch (NumberFormatException e) {
                            return DEDUP_WINDOW_DEFAULT;
                        }
                    })
                    .orElse(DEDUP_WINDOW_DEFAULT);
        } catch (Exception e) {
            return DEDUP_WINDOW_DEFAULT;
        }
    }

    private BigDecimal resolveRequestQty(ScanDtos.ScanRequest req, BigDecimal defaultQty) {
        BigDecimal qty = req.requestQty == null ? defaultQty : req.requestQty;
        return guard.positive(qty, "本次申请数量");
    }

    private String resolveRequestUnit(ScanDtos.ScanRequest req) {
        String unit = firstNonBlank(req.requestUnit, "个");
        if (unit.length() > 32) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "申请单位不能超过32个字符");
        }
        return unit;
    }

    private String findMaterialUnit(String materialCode) {
        if (materialCode == null || materialCode.isBlank()) return null;
        return materialRepository.findByMaterialCode(materialCode.trim())
                .map(MaterialEntity::getUnit)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ScanDtos.ScanPreviewResult preview(ScanDtos.ScanRequest req) {
        if (req == null) req = new ScanDtos.ScanRequest();
        String raw = firstNonBlank(req.scanCode, req.labelCode);
        raw = labelResolverService.normalize(raw);

        String[] parsed = splitMaterialAndStation(raw);
        String scanCode = parsed[0];
        String stationCode = firstNonBlank(req.stationCode, parsed[1]);
        String usageType = firstNonBlank(req.usageType, parsed[2]);

        try {
            LabelEntity label = labelResolverService.resolve(scanCode);
            validateLabelReadyForPull(label);
            MappingScope previewScope = snapshotOrMappingScope(label.getFactory(), label.getDeliveryArea(),
                    label.getWarehouseCode(), label.getMaterialCode());
            dataScopeService.requireAccessFactoryArea(previewScope.factory(), previewScope.deliveryArea());
            BoxEntity box = boxRepository.findByLabelCode(label.getLabelCode()).orElse(null);

            BigDecimal defaultQty = box != null
                    && box.getStandardQty() != null
                    && box.getStandardQty().compareTo(BigDecimal.ZERO) > 0
                    ? box.getStandardQty()
                    : label.getStandardQty();

            ScanDtos.ScanPreviewResult r = new ScanDtos.ScanPreviewResult();
            r.scannedCode = scanCode;
            r.materialCode = firstNonBlank(label.getMaterialCode(), box == null ? null : box.getMaterialCode());
            r.materialName = firstNonBlank(label.getMaterialName(), box == null ? null : box.getMaterialName(), r.materialCode);
            r.warehouseCode = firstNonBlank(label.getWarehouseCode(), box == null ? null : box.getWarehouseCode());
            r.warehouseAddress = firstNonBlank(label.getWarehouseAddress(), box == null ? null : box.getWarehouseAddress());
            r.warehouseLocation = firstNonBlank(label.getWarehouseLocation(), box == null ? null : box.getWarehouseLocation());
            r.sendStationAddress = firstNonBlank(label.getSendStationAddress(), box == null ? null : box.getSendStationAddress());
            r.deliveryAddress = firstNonBlank(label.getDeliveryAddress(), box == null ? null : box.getDeliveryAddress());
            r.stationCode = firstNonBlank(label.getStationCode(), box == null ? null : box.getStationCode(), stationCode);
            r.defaultQty = guard.positive(defaultQty, "默认申请数量");
            r.defaultUnit = firstNonBlank(label.getUnit(), findMaterialUnit(r.materialCode), "个");
            return r;
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.NOT_FOUND) throw e;
        }

        boolean spare = "SPARE".equalsIgnoreCase(firstNonBlank(usageType, ""));
        MaterialMappingEntity mapping = chooseMapping(scanCode, stationCode, spare);
        Optional<StationMaterialEntity> station = resolveStation(scanCode, stationCode);

        ScanDtos.ScanPreviewResult r = new ScanDtos.ScanPreviewResult();
        r.scannedCode = scanCode;
        r.materialCode = scanCode;
        r.materialName = station.map(StationMaterialEntity::getMaterialName).orElse(scanCode);
        r.warehouseCode = mapping.getWarehouseCode();
        r.warehouseAddress = mapping.getWarehouseLocation();
        r.warehouseLocation = mapping.getWarehouseLocation();
        r.sendStationAddress = firstNonBlank(mapping.getDeliveryAddress(), stationCode);
        r.deliveryAddress = r.sendStationAddress;
        r.stationCode = stationCode;
        r.defaultQty = guard.positive(mapping.getQuantity(), "默认申请数量");
        r.defaultUnit = firstNonBlank(findMaterialUnit(scanCode), "个");
        return r;
    }
}
