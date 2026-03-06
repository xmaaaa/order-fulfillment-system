package com.xm.scenario.concurrent.lock.delegate;

import com.xm.scenario.concurrent.lock.LockStrategy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 学习用：基于 JVM 内 ReentrantLock 的锁实现，单机多线程有效。
 * 多机需用 RedissonLockStrategy（xm-spring）。
 */
public class InMemoryLockStrategy implements LockStrategy {

    private final String lockType;
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public InMemoryLockStrategy(String lockType) {
        this.lockType = lockType == null ? "IN_MEMORY" : lockType;
    }

    @Override
    public String getLockType() {
        return lockType;
    }

    @Override
    public boolean tryLock(String resourceKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        ReentrantLock lock = lockMap.computeIfAbsent(resourceKey, k -> new ReentrantLock());
        return lock.tryLock(waitTime, unit);
    }

    @Override
    public void unlock(String resourceKey) {
        ReentrantLock lock = lockMap.get(resourceKey);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public boolean isHeldByCurrentThread(String resourceKey) {
        ReentrantLock lock = lockMap.get(resourceKey);
        return lock != null && lock.isHeldByCurrentThread();
    }
}
