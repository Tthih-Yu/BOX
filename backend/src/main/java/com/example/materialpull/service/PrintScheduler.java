package com.example.materialpull.service;

import com.example.materialpull.resilience.LockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时自动打印调度器。固定每分钟唤醒一次，是否真正触发由 {@link AutoPrintService} 按后台配置的
 * 间隔/开关判断，因此用户在“系统参数”里改间隔后无需重启即可生效。
 * 多实例部署时用锁租约去重，保证同一时刻只有一个实例执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrintScheduler {
    private final LockProvider lockProvider;
    private final AutoPrintService autoPrintService;

    @Scheduled(fixedDelayString = "${app.scheduler.auto-print-check-ms:60000}")
    public void tick() {
        boolean ran = lockProvider.tryExecute("SCHEDULER:AUTO_PRINT", 300, autoPrintService::runAutoPrint);
        if (!ran) log.debug("定时自动打印已被其它实例执行，本实例跳过");
    }
}
