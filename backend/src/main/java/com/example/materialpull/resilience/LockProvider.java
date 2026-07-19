package com.example.materialpull.resilience;

import java.util.function.Supplier;

/**
 * 业务锁抽象。单实例用本地 ReentrantLock；多实例用 Redis 分布式锁。
 * 用于防止同一料箱 / 任务被并发重复操作。
 */
public interface LockProvider {
    <T> T execute(String key, Supplier<T> supplier);

    /**
     * 尝试获取锁并执行：抢到锁才执行 task 并返回 true；抢不到立即返回 false（不等待、不抛异常）。
     * 用于定时任务多实例去重——同一时刻只让一个实例执行。
     *
     * @param leaseSeconds 锁租约秒数，应大于任务预计执行时长，防止任务未完锁先释放。
     */
    boolean tryExecute(String key, long leaseSeconds, Runnable task);
}
