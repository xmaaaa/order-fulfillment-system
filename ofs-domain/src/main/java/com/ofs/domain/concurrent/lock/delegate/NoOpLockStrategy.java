package com.ofs.domain.concurrent.lock.delegate;

import com.ofs.domain.concurrent.lock.LockStrategy;

import java.util.concurrent.TimeUnit;

/**
 * 学习用：无锁实现，便于单测/本地跑通。lock=none 时等效。
 */
public class NoOpLockStrategy implements LockStrategy {

    @Override
    public String getLockType() {
        return "NO_OP";
    }

    @Override
    public boolean tryLock(String resourceKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        return true;
    }

    @Override
    public void unlock(String resourceKey) {
        // no-op
    }

    @Override
    public boolean isHeldByCurrentThread(String resourceKey) {
        return false;
    }
}
