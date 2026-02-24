package com.xm.scenario.concurrent.lock;

import java.util.concurrent.TimeUnit;

/**
 * 库存维锁：粒度 skuId + warehouseId，高竞争资源。
 * 真实场景：lease 较短（如 10s），避免热 SKU 长时间阻塞；key 含仓库区分多仓。
 */
public interface InventoryLockStrategy {

    boolean tryLock(String skuId, String warehouseId, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    void unlock(String skuId, String warehouseId);

    default boolean tryLock(String skuId, String warehouseId, long waitTimeSec, long leaseTimeSec) throws InterruptedException {
        return tryLock(skuId, warehouseId, waitTimeSec, leaseTimeSec, TimeUnit.SECONDS);
    }
}
