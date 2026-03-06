package com.xm.scenario.concurrent.lock.delegate;

import com.xm.scenario.concurrent.lock.InventoryLockStrategy;
import com.xm.scenario.concurrent.lock.LockStrategy;

import java.util.concurrent.TimeUnit;

/**
 * 库存锁：委托底层 LockStrategy，key=inventory:{skuId}:{warehouseId}，lease 默认 10s（高竞争）。
 */
public class DelegatingInventoryLockStrategy implements InventoryLockStrategy {

    private static final String KEY_PREFIX = "inventory:";
    private static final long DEFAULT_LEASE_SEC = 10;

    private final LockStrategy delegate;

    public DelegatingInventoryLockStrategy(LockStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean tryLock(String skuId, String warehouseId, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        String key = KEY_PREFIX + skuId + ":" + (warehouseId == null ? "default" : warehouseId);
        long lease = leaseTime <= 0 ? DEFAULT_LEASE_SEC : leaseTime;
        return delegate.tryLock(key, waitTime, lease, unit);
    }

    @Override
    public void unlock(String skuId, String warehouseId) {
        String key = KEY_PREFIX + skuId + ":" + (warehouseId == null ? "default" : warehouseId);
        delegate.unlock(key);
    }
}
