package com.example.materialpull.service;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.entity.SystemConfigEntity;
import com.example.materialpull.enums.TaskStatus;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import com.example.materialpull.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时检查调度入口。只读地取出在途任务号清单，再逐条交给 {@link TimeoutTaskProcessor} 用独立事务处理。
 * 这样单条任务的乐观锁冲突/异常只会重试或跳过该条，不会像原实现那样整批回滚，
 * 从而修复“正常任务超时后没有升级为紧急任务”的问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutMaintenanceService {
    private final ReplenishmentTaskRepository taskRepository;
    private final AppProperties properties;
    private final SystemConfigRepository configRepository;
    private final TimeoutTaskProcessor processor;

    /** 正常任务自动升级紧急的超时(分钟)配置键。后台“系统参数”页可改，定时检查时实时读取，无需重启。 */
    private static final String AUTO_URGENT_KEY = "task.auto-urgent.minutes";

    public void runTimeoutCheck() {
        long autoUrgentMinutes = autoUrgentMinutes();
        List<String> taskNos = pendingTaskNos();
        int limit = Math.max(1, properties.getRecoverBatchSize());
        int processed = 0;
        for (String taskNo : taskNos) {
            if (processed++ >= limit) break;
            try {
                processor.process(taskNo, autoUrgentMinutes);
            } catch (RuntimeException e) {
                // 单条失败（含并发导致的乐观锁冲突）只记日志，下一轮定时会再处理，不影响其余任务
                log.warn("超时检查处理任务失败 taskNo={} msg={}", taskNo, e.getMessage());
            }
        }
    }

    /** 只读取出当前在途任务号，避免持有实体到逐条处理阶段引发脏读/长事务。 */
    private List<String> pendingTaskNos() {
        List<TaskStatus> doing = List.of(TaskStatus.CREATED, TaskStatus.ACCEPTED, TaskStatus.PICKING,
                TaskStatus.PICKED, TaskStatus.DELIVERING, TaskStatus.ARRIVED);
        return taskRepository.findByStatusIn(doing).stream()
                .map(ReplenishmentTaskEntity::getTaskNo)
                .toList();
    }

    /**
     * 读取“正常任务自动升级紧急”的超时分钟数。优先读 sys_config 的 task.auto-urgent.minutes，
     * 每次定时检查实时读取，后台改完立即生效；缺失或非法时回退 0（关闭自动升级）。
     */
    private long autoUrgentMinutes() {
        try {
            return configRepository.findByConfigKey(AUTO_URGENT_KEY)
                    .map(SystemConfigEntity::getConfigValue)
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> {
                        try {
                            long minutes = Long.parseLong(v.trim());
                            return minutes < 0 ? 0L : minutes;
                        } catch (NumberFormatException e) {
                            return 0L;
                        }
                    })
                    .orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }
}
