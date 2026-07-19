package com.example.materialpull.resilience;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 本地业务锁（默认）。等价改造前 BusinessLockService 的 ReentrantLock 实现。
 * 仅在单实例运行时互斥；多实例请启用集群模式使用 Redis 分布式锁。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cluster", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalLockProvider implements LockProvider {
    private final AppProperties properties;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T execute(String key, Supplier<T> supplier) {
        if (key == null || key.isBlank()) return supplier.get();
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        boolean acquired = false;
        try {
            acquired = lock.tryLock(properties.getLockWaitMs(), TimeUnit.MILLISECONDS);
            if (!acquired) throw new BusinessException(ErrorCode.LOCK_TIMEOUT, "业务正在处理中，请不要重复操作：" + key);
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_TIMEOUT, "业务锁等待被中断，请稍后重试");
        } finally {
            if (acquired) lock.unlock();
            if (!lock.isLocked() && !lock.hasQueuedThreads()) locks.remove(key, lock);
        }
    }

    @Override
    public boolean tryExecute(String key, long leaseSeconds, Runnable task) {
        // 单实例下不存在跨进程竞争，用本地锁仅防止同一实例内任务重叠执行
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        if (!lock.tryLock()) return false;
        try {
            task.run();
            return true;
        } finally {
            lock.unlock();
            if (!lock.isLocked() && !lock.hasQueuedThreads()) locks.remove(key, lock);
        }
    }
}
