package com.xm.scenario.concurrent.lock;

import java.util.concurrent.TimeUnit;

/**
 * 用户维锁：粒度 userId，限制单用户并发下单数或防重复提交。
 * 真实场景：lease 较短（如 5s）；或为信号量（单用户最多 N 个订单在创建）。
 */
public interface UserLockStrategy {

    boolean tryLock(String userId, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    void unlock(String userId);

    default boolean tryLock(String userId, long waitTimeSec, long leaseTimeSec) throws InterruptedException {
        return tryLock(userId, waitTimeSec, leaseTimeSec, TimeUnit.SECONDS);
    }
}
