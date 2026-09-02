package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.resilience.LockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyPlanScheduler {
    private final LockProvider lockProvider;
    private final WeeklyPlanCalcService calcService;

    @Value("${app.scheduler.weekly-plan-zone:Asia/Shanghai}")
    private String zone;

    @Scheduled(
            cron = "${app.scheduler.weekly-plan-refresh-cron:0 0 0 * * *}",
            zone = "${app.scheduler.weekly-plan-zone:Asia/Shanghai}"
    )
    public void refreshToday() {
        LocalDate today = LocalDate.now(ZoneId.of(zone));
        boolean ran = lockProvider.tryExecute("SCHEDULER:WEEKLY_PLAN_DAILY:" + today, 1800, () -> refresh(today));
        if (!ran) log.info("当天用量自动刷新已由其它实例执行，日期={}", today);
    }

    private void refresh(LocalDate planDate) {
        try {
            WeeklyPlanCalcService.CalcResult result = calcService.calculateSystem(planDate, true);
            log.info("当天用量自动刷新完成，日期={}，物料={}，组件={}，更新映射={}，未维护单根用量跳过={}，提示={}",
                    planDate,
                    result.materialCount,
                    result.componentCount,
                    result.affectedMappings,
                    result.skippedNoUsage,
                    result.warnings.size());
        } catch (BusinessException e) {
            log.warn("当天用量自动刷新跳过，日期={}，原因={}", planDate, e.getMessage());
        } catch (Exception e) {
            log.error("当天用量自动刷新失败，日期={}", planDate, e);
        }
    }
}
