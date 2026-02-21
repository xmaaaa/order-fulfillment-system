package com.xm.scenario.concurrent.lock;

import java.util.concurrent.TimeUnit;

/**
 * 底层锁策略抽象（key 透传）。业务侧用 OrderLockStrategy、InventoryLockStrategy、UserLockStrategy，
 * 三者语义与 lease 不同：订单 30s、库存 10s、用户 5s。
 */
public interface LockStrategy {

    /**
     * 锁类型标识（用于组合锁时约定加锁顺序，避免死锁）
     */
    String getLockType();

    /**
     * 尝试获取锁
     *
     * @param resourceKey 资源键（如 orderId、skuId、userId）
     * @param waitTime    等待时间
     * @param leaseTime   持有时间，-1 表示看门狗续期
     * @param unit        时间单位
     * @return 是否获取成功
     */
    boolean tryLock(String resourceKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * 释放锁（仅当前线程持有时才释放）
     */
    void unlock(String resourceKey);

    /**
     * 是否由当前线程持有
     */
    boolean isHeldByCurrentThread(String resourceKey);
}
