package com.example.materialpull.service;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.enums.PriorityLevel;
import com.example.materialpull.enums.TaskStatus;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import com.example.materialpull.resilience.BusinessLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutScheduler {
    private final ReplenishmentTaskRepository taskRepository;
    private final RealtimePushService pushService;
    private final AuditService auditService;
    private final SystemAlertService alertService;
    private final BusinessLockService lockService;
    private final AppProperties properties;

    @Scheduled(fixedDelayString = "${app.scheduler.timeout-check-ms:60000}")
    @Transactional
    public void checkTimeout() {
        lockService.execute("SCHEDULER:TIMEOUT", () -> {
            List<TaskStatus> doing = List.of(TaskStatus.CREATED, TaskStatus.ACCEPTED, TaskStatus.PICKING, TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED);
            int count = 0;
            for (ReplenishmentTaskEntity t : taskRepository.findByStatusIn(doing)) {
                if (count++ > properties.getRecoverBatchSize()) break;
                LocalDateTime now = LocalDateTime.now();
                if (t.getDeadlineAt() != null && now.isAfter(t.getDeadlineAt())) {
                    t.setPriority(PriorityLevel.URGENT);
                    t.setWarningAt(now);
                    t.setLastError("任务已超过截止时间，已提升为紧急任务");
                    taskRepository.save(t);
                    auditService.task(t.getTaskNo(), "TIMEOUT_WARN", t.getStatus().name(), t.getStatus().name(), "SCHEDULER", t.getLastError());
                    alertService.open("WARN", "TASK_TIMEOUT", t.getTaskNo(), "补货任务超时", t.getLastError());
                    pushService.publish("taskWarnings", t);
                }
                if (t.getUpdatedAt() != null && t.getUpdatedAt().isBefore(now.minusMinutes(properties.getStuckTaskMinutes()))) {
                    t.setStuckFlag(true);
                    t.setPriority(PriorityLevel.URGENT);
                    t.setLastError("任务长时间未更新，系统防卡死标记");
                    taskRepository.save(t);
                    auditService.task(t.getTaskNo(), "STUCK_GUARD", t.getStatus().name(), t.getStatus().name(), "SCHEDULER", t.getLastError());
                    pushService.publish("taskWarnings", t);
                }
            }
            return null;
        });
    }
}
