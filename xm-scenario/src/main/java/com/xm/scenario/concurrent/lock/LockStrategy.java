package com.xm.scenario.concurrent.lock;

import java.util.concurrent.TimeUnit;

/**
 * 锁策略抽象：不同粒度（订单、库存、用户、第三方）由不同实现提供。
 * 可与 Redisson 等结合，在 xm-spring 中注入具体实现。
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
