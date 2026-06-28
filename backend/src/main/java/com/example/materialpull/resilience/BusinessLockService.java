package com.example.materialpull.resilience;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class BusinessLockService {
    private final AppProperties properties;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

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
}
