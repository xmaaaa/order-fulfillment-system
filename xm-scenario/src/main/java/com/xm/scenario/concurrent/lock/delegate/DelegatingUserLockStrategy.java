package com.xm.scenario.concurrent.lock.delegate;

import com.xm.scenario.concurrent.lock.LockStrategy;
import com.xm.scenario.concurrent.lock.UserLockStrategy;

import java.util.concurrent.TimeUnit;

/**
 * 用户锁：委托底层 LockStrategy，key=user:{userId}，lease 默认 5s。
 */
public class DelegatingUserLockStrategy implements UserLockStrategy {

    private static final String KEY_PREFIX = "user:";
    private static final long DEFAULT_LEASE_SEC = 5;

    private final LockStrategy delegate;

    public DelegatingUserLockStrategy(LockStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean tryLock(String userId, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long lease = leaseTime <= 0 ? DEFAULT_LEASE_SEC : leaseTime;
        return delegate.tryLock(KEY_PREFIX + userId, waitTime, lease, unit);
    }

    @Override
    public void unlock(String userId) {
        delegate.unlock(KEY_PREFIX + userId);
    }
}
