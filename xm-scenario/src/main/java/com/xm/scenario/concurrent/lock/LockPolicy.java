package com.xm.scenario.concurrent.lock;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 锁策略组合：按约定顺序加多把锁（如先订单锁再库存锁），执行业务后统一释放。
 * 实现时需保证：同一业务内加锁顺序全局一致，避免死锁。
 */
public interface LockPolicy {

    /**
     * 在锁保护下执行任务（订单 + 库存等组合由实现决定）
     *
     * @param orderId    订单 ID
     * @param skuIds     涉及 SKU（可选，用于库存锁）
     * @param userId     用户 ID（可选，用于用户维锁）
     * @param task       业务任务
     * @param waitTime   等待锁时间
     * @param leaseTime  锁持有时间（秒），-1 表示看门狗
     * @return 任务结果，若未拿到锁可返回 null 或抛异常
     */
    <T> T executeWithLock(String orderId, List<String> skuIds, String userId,
                          Callable<T> task, long waitTime, long leaseTime) throws Exception;

    /**
     * 仅订单维锁（简单场景）
     */
    default <T> T executeWithOrderLock(String orderId, Callable<T> task, long waitTime, long leaseTime) throws Exception {
        return executeWithLock(orderId, null, null, task, waitTime, leaseTime);
    }
}
