package com.xm.scenario.concurrent.lock.delegate;

import com.xm.scenario.concurrent.lock.LockStrategy;
import com.xm.scenario.concurrent.lock.OrderLockStrategy;

import java.util.concurrent.TimeUnit;

/**
 * 订单锁：委托底层 LockStrategy，key=order:{orderId}，lease 默认 30s。
 */
public class DelegatingOrderLockStrategy implements OrderLockStrategy {

    private static final String KEY_PREFIX = "order:";
    private static final long DEFAULT_LEASE_SEC = 30;

    private final LockStrategy delegate;

    public DelegatingOrderLockStrategy(LockStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean tryLock(String orderId, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long lease = leaseTime <= 0 ? DEFAULT_LEASE_SEC : leaseTime;
        return delegate.tryLock(KEY_PREFIX + orderId, waitTime, lease, unit);
    }

    @Override
    public void unlock(String orderId) {
        delegate.unlock(KEY_PREFIX + orderId);
    }
}
