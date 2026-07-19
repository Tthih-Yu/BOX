package com.example.materialpull.service;

import com.example.materialpull.resilience.LockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutScheduler {
    private final LockProvider lockProvider;
    private final TimeoutMaintenanceService timeoutMaintenanceService;

    @Scheduled(fixedDelayString = "${app.scheduler.timeout-check-ms:60000}")
    public void checkTimeout() {
        // 多实例部署时同一时刻只允许一个实例执行：集群模式用 Redis 租约去重，
        // 单实例下退化为本地锁。租约 300 秒，远大于单次执行耗时。
        boolean ran = lockProvider.tryExecute("SCHEDULER:TIMEOUT", 300, timeoutMaintenanceService::runTimeoutCheck);
        if (!ran) log.debug("超时检查已被其它实例执行，本实例跳过");
    }
}
