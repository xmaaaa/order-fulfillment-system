package com.xm.scenario.concurrent.lock;

import java.util.concurrent.TimeUnit;

/**
 * 订单维锁：粒度 orderId，防止同一订单并发修改。
 * 真实场景：订单锁 lease 较长（如 30s），因订单操作可能涉及支付、库存等下游调用。
 */
public interface OrderLockStrategy {

    boolean tryLock(String orderId, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    void unlock(String orderId);

    default boolean tryLock(String orderId, long waitTimeSec, long leaseTimeSec) throws InterruptedException {
        return tryLock(orderId, waitTimeSec, leaseTimeSec, TimeUnit.SECONDS);
    }
}
