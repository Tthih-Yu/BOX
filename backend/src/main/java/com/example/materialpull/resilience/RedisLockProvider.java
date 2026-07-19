package com.example.materialpull.resilience;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.config.RedisConfig.ClusterKeyspace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 分布式业务锁（集群模式）。
 * 用 SET key value NX PX 抢锁，自带过期时间防止持有者宕机死锁；
 * 释放时用 Lua 脚本比对持有者标识，避免误删别人的锁。
 */
@Component
@ConditionalOnProperty(prefix = "app.cluster", name = "enabled", havingValue = "true")
public class RedisLockProvider implements LockProvider {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** 锁最长持有时间，超过即自动释放，防止异常导致死锁。 */
    private static final long LEASE_MS = 30_000L;
    private static final long RETRY_INTERVAL_MS = 50L;

    private final StringRedisTemplate redis;
    private final AppProperties properties;
    private final ClusterKeyspace keys;

    public RedisLockProvider(StringRedisTemplate redis, AppProperties properties, ClusterKeyspace keys) {
        this.redis = redis;
        this.properties = properties;
        this.keys = keys;
    }

    @Override
    public <T> T execute(String key, Supplier<T> supplier) {
        if (key == null || key.isBlank()) return supplier.get();
        String redisKey = keys.of("lock", key);
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + properties.getLockWaitMs();
        boolean acquired = false;
        try {
            while (System.currentTimeMillis() <= deadline) {
                Boolean ok = redis.opsForValue().setIfAbsent(redisKey, token, Duration.ofMillis(LEASE_MS));
                if (Boolean.TRUE.equals(ok)) {
                    acquired = true;
                    break;
                }
                Thread.sleep(RETRY_INTERVAL_MS);
            }
            if (!acquired) throw new BusinessException(ErrorCode.LOCK_TIMEOUT, "业务正在处理中，请不要重复操作：" + key);
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_TIMEOUT, "业务锁等待被中断，请稍后重试");
        } finally {
            if (acquired) {
                try {
                    redis.execute(UNLOCK_SCRIPT, Collections.singletonList(redisKey), token);
                } catch (Exception ignored) {
                    // 锁会随租约到期自动释放，释放失败不影响业务结果
                }
            }
        }
    }

    @Override
    public boolean tryExecute(String key, long leaseSeconds, Runnable task) {
        if (key == null || key.isBlank()) {
            task.run();
            return true;
        }
        String redisKey = keys.of("lease", key);
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(redisKey, token, Duration.ofSeconds(Math.max(1, leaseSeconds)));
        if (!Boolean.TRUE.equals(ok)) return false;
        try {
            task.run();
            return true;
        } finally {
            try {
                redis.execute(UNLOCK_SCRIPT, Collections.singletonList(redisKey), token);
            } catch (Exception ignored) {
                // 租约到期自动释放
            }
        }
    }
}
