package com.example.materialpull.service;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.enums.PriorityLevel;
import com.example.materialpull.enums.TaskStatus;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 单条任务的超时检查处理。每条任务独立事务（REQUIRES_NEW）并对任务加悲观写锁重读，
 * 避免与现场扫码/仓库操作产生乐观锁冲突导致整批回滚——这是“超时未自动升级紧急”的根因之一。
 * 任一条任务处理异常只影响该条，不影响其余任务。
 *
 * 关键约束：状态变更（升级紧急/防卡死标记）与其“通知类副作用”（审计/告警/实时推送）必须解耦。
 * 副作用一律 try/catch 包裹，绝不能因为写审计或开告警失败而把已经算好的“升级紧急”整体回滚，
 * 否则会出现“每轮都判定要升级、但每轮都因副作用异常回滚，导致永远升不了级”的隐性故障。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutTaskProcessor {
    private final ReplenishmentTaskRepository taskRepository;
    private final RealtimePushService pushService;
    private final AuditService auditService;
    private final SystemAlertService alertService;
    private final AppProperties properties;

    /**
     * 处理单条任务的三类超时：
     * 1) 正常任务超过 autoUrgentMinutes 未完成 → 升级紧急；
     * 2) 超过截止时间 deadlineAt → 升级紧急；
     * 3) 超过 stuckTaskMinutes 未更新 → 防卡死标记并升级紧急。
     * 用悲观写锁重读任务，保证读到的是提交后的最新状态。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String taskNo, long autoUrgentMinutes) {
        ReplenishmentTaskEntity t = taskRepository.findByTaskNoForUpdate(taskNo).orElse(null);
        if (t == null) return;
        if (isTerminal(t.getStatus())) return;

        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;

        if (autoUrgentMinutes > 0 && t.getPriority() != PriorityLevel.URGENT
                && t.getCreatedAt() != null && t.getCreatedAt().isBefore(now.minusMinutes(autoUrgentMinutes))) {
            t.setPriority(PriorityLevel.URGENT);
            t.setDeliveryMode("URGENT");
            t.setWarningAt(now);
            t.setLastError("正常配送任务超过" + autoUrgentMinutes + "分钟未完成，已自动升级为紧急任务");
            taskRepository.save(t);
            log.info("任务自动升级紧急 taskNo={} 超时阈值={}分钟 创建时间={}", t.getTaskNo(), autoUrgentMinutes, t.getCreatedAt());
            safeAudit(t, "AUTO_URGENT");
            safeAlert("TASK_AUTO_URGENT", t, "补货任务自动升级紧急");
            safePublish("taskWarnings", t);
            changed = true;
        }

        if (t.getDeadlineAt() != null && now.isAfter(t.getDeadlineAt()) && t.getPriority() != PriorityLevel.URGENT) {
            t.setPriority(PriorityLevel.URGENT);
            t.setDeliveryMode("URGENT");
            t.setWarningAt(now);
            t.setLastError("任务已超过截止时间，已提升为紧急任务");
            taskRepository.save(t);
            log.info("任务超过截止时间升级紧急 taskNo={} 截止时间={}", t.getTaskNo(), t.getDeadlineAt());
            safeAudit(t, "TIMEOUT_WARN");
            safeAlert("TASK_TIMEOUT", t, "补货任务超时");
            safePublish("taskWarnings", t);
            changed = true;
        }

        if (!Boolean.TRUE.equals(t.getStuckFlag()) && t.getUpdatedAt() != null
                && t.getUpdatedAt().isBefore(now.minusMinutes(properties.getStuckTaskMinutes()))) {
            t.setStuckFlag(true);
            t.setPriority(PriorityLevel.URGENT);
            t.setLastError("任务长时间未更新，系统防卡死标记");
            taskRepository.save(t);
            log.info("任务防卡死标记并升级紧急 taskNo={} 更新时间={}", t.getTaskNo(), t.getUpdatedAt());
            safeAudit(t, "STUCK_GUARD");
            safePublish("taskWarnings", t);
            changed = true;
        }

        if (changed) safePublish("tasks", t);
    }

    /** 写审计失败不得回滚“升级紧急”这一核心状态变更。 */
    private void safeAudit(ReplenishmentTaskEntity t, String action) {
        try {
            auditService.task(t.getTaskNo(), action, t.getStatus().name(), t.getStatus().name(), "SCHEDULER", t.getLastError());
        } catch (RuntimeException e) {
            log.warn("超时升级写审计失败(不影响升级本身) taskNo={} action={} msg={}", t.getTaskNo(), action, e.getMessage());
        }
    }

    /** 开告警失败不得回滚“升级紧急”这一核心状态变更。 */
    private void safeAlert(String category, ReplenishmentTaskEntity t, String title) {
        try {
            alertService.open("WARN", category, t.getTaskNo(), title, t.getLastError());
        } catch (RuntimeException e) {
            log.warn("超时升级开告警失败(不影响升级本身) taskNo={} category={} msg={}", t.getTaskNo(), category, e.getMessage());
        }
    }

    /** 实时推送失败不得回滚“升级紧急”这一核心状态变更。 */
    private void safePublish(String topic, ReplenishmentTaskEntity t) {
        try {
            pushService.publish(topic, t);
        } catch (RuntimeException e) {
            log.warn("超时升级实时推送失败(不影响升级本身) taskNo={} topic={} msg={}", t.getTaskNo(), topic, e.getMessage());
        }
    }

    private boolean isTerminal(TaskStatus s) {
        return s == TaskStatus.COMPLETED || s == TaskStatus.CANCELLED || s == TaskStatus.EXCEPTION;
    }
}
