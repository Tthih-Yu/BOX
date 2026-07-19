package com.example.materialpull.service;

import com.example.materialpull.common.*;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BoxPoolService {
    private final BoxPoolRepository boxPoolRepository;
    private final RealtimePushService pushService;

    public List<BoxPoolEntity> list(String status) {
        if (status == null || status.isBlank()) return boxPoolRepository.findTop1000ByOrderByUpdatedAtDesc();
        BoxPoolStatus s;
        try {
            s = BoxPoolStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知容器状态：" + status);
        }
        return boxPoolRepository.findTop1000ByStatusOrderByUpdatedAtDesc(s);
    }

    @Transactional
    public BoxPoolEntity save(BoxPoolEntity box) {
        if (box == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "容器不能为空");
        if (box.getContainerNo() == null || box.getContainerNo().isBlank()) box.setContainerNo(IdGenerator.id("BOXPOOL"));
        if (box.getStatus() == null) box.setStatus(BoxPoolStatus.AVAILABLE);
        BoxPoolEntity saved = boxPoolRepository.save(box);
        pushService.publish("boxPool", saved);
        return saved;
    }

    @Transactional
    public BoxPoolEntity allocateForTask(ReplenishmentTaskEntity task) {
        if (task == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "任务不能为空");
        List<BoxPoolEntity> candidates = boxPoolRepository.findCandidatesForUpdate(BoxPoolStatus.AVAILABLE, blankToNull(task.getBoxSize()));
        if (candidates.isEmpty()) candidates = boxPoolRepository.findCandidatesForUpdate(BoxPoolStatus.AVAILABLE, null);
        BoxPoolEntity box;
        if (candidates.isEmpty()) {
            box = new BoxPoolEntity();
            box.setContainerNo(IdGenerator.id("CBOX"));
            box.setContainerType(firstNonBlank(task.getBoxSize(), "周转箱"));
            box.setBoxSize(task.getBoxSize());
            box.setWarehouseCode(task.getWarehouseCode());
            box.setCurrentLocation(firstNonBlank(task.getWarehouseAddress(), task.getWarehouseLocation(), "仓库"));
            box.setRemark("系统自动创建周转容器；现场无专用盒时由仓库统一发盒");
        } else {
            box = candidates.get(0);
        }
        box.setTaskNo(task.getTaskNo());
        box.setStationCode(task.getStationCode());
        box.setMaterialCode(task.getMaterialCode());
        box.setWarehouseMaterialCode(task.getWarehouseMaterialCode());
        box.setStatus(BoxPoolStatus.ALLOCATED);
        box.setLockedFlag(true);
        box.setLastAllocatedAt(LocalDateTime.now());
        boxPoolRepository.save(box);
        pushService.publish("boxPool", box);
        return box;
    }

    @Transactional
    public void markDelivering(ReplenishmentTaskEntity task) {
        for (BoxPoolEntity box : boxPoolRepository.findByTaskNo(task.getTaskNo())) {
            box.setStatus(BoxPoolStatus.IN_TRANSIT);
            box.setCurrentLocation("AGV配送中");
            boxPoolRepository.save(box);
            pushService.publish("boxPool", box);
        }
    }

    @Transactional
    public void markDeliveredToLine(ReplenishmentTaskEntity task) {
        for (BoxPoolEntity box : boxPoolRepository.findByTaskNo(task.getTaskNo())) {
            box.setStatus(BoxPoolStatus.AT_LINE);
            box.setCurrentLocation(firstNonBlank(task.getSendStationAddress(), task.getDeliveryAddress(), task.getStationCode()));
            box.setLastDeliveredAt(LocalDateTime.now());
            box.setLockedFlag(false);
            box.setCycleCount((box.getCycleCount() == null ? 0 : box.getCycleCount()) + 1);
            boxPoolRepository.save(box);
            pushService.publish("boxPool", box);
        }
    }

    @Transactional
    public BoxPoolEntity returnEmpty(String containerNo, String taskNo, String location, String operator) {
        final String normalizedContainerNo = firstNonBlank(containerNo);
        if (normalizedContainerNo == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "容器编号不能为空");
        BoxPoolEntity box = boxPoolRepository.findByContainerNoForUpdate(normalizedContainerNo).orElseGet(() -> {
            BoxPoolEntity created = new BoxPoolEntity();
            created.setContainerNo(normalizedContainerNo);
            created.setContainerType("现场空盒");
            created.setRemark("现场回收时自动建档，操作人=" + firstNonBlank(operator, OperatorResolver.systemOperator()));
            return created;
        });
        box.setTaskNo(taskNo);
        box.setStatus(BoxPoolStatus.EMPTY_RETURNING);
        box.setCurrentLocation(firstNonBlank(location, "AGV空盒回库中"));
        box.setLockedFlag(true);
        box.setLastReturnedAt(LocalDateTime.now());
        box.setRemark("空盒回收扫码，操作人=" + firstNonBlank(operator, OperatorResolver.systemOperator()));
        boxPoolRepository.save(box);
        pushService.publish("boxPool", box);
        return box;
    }

    @Transactional
    public void releaseByTaskNo(String taskNo, String operator) {
        if (taskNo == null || taskNo.isBlank()) return;
        for (BoxPoolEntity box : boxPoolRepository.findByTaskNo(taskNo)) {
            box.setTaskNo(null);
            box.setStatus(BoxPoolStatus.AVAILABLE);
            box.setCurrentLocation(firstNonBlank(box.getCurrentLocation(), "仓库空箱区"));
            box.setLockedFlag(false);
            box.setRemark("任务取消释放周转箱，操作人=" + firstNonBlank(operator, OperatorResolver.systemOperator()));
            boxPoolRepository.save(box);
            pushService.publish("boxPool", box);
        }
    }

    @Transactional
    public BoxPoolEntity backToWarehouse(String containerNo, String location) {
        BoxPoolEntity box = boxPoolRepository.findByContainerNoForUpdate(containerNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "容器不存在：" + containerNo));
        box.setTaskNo(null);
        box.setStatus(BoxPoolStatus.AVAILABLE);
        box.setCurrentLocation(firstNonBlank(location, "仓库空箱区"));
        box.setLockedFlag(false);
        boxPoolRepository.save(box);
        pushService.publish("boxPool", box);
        return box;
    }


    public String findContainerNoForTask(String taskNo) {
        return boxPoolRepository.findByTaskNo(taskNo).stream().findFirst().map(BoxPoolEntity::getContainerNo).orElse(null);
    }

    private String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v.trim(); return null; }
}
