package com.example.materialpull.resilience;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 业务锁门面。保留原有注入点（多个 Service 注入本类），
 * 实际加锁逻辑委托给 {@link LockProvider}：
 * 单实例用 {@link LocalLockProvider}，集群模式用 {@link RedisLockProvider}。
 */
@Service
@RequiredArgsConstructor
public class BusinessLockService {
    private final LockProvider lockProvider;

    public <T> T execute(String key, Supplier<T> supplier) {
        return lockProvider.execute(key, supplier);
    }
}
