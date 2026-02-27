package com.xm.scenario.concurrent.lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 组合锁策略：按 LockOrder 顺序加锁（ORDER -> INVENTORY -> USER），执行后逆序释放。
 * 订单锁、库存锁、用户锁语义与配置不同：订单 lease 长、库存 lease 短、用户 lease 短。
 */
public class CompositeLockPolicy implements LockPolicy {

    private static final long DEFAULT_WAIT_SEC = 5;

    private final OrderLockStrategy orderLockStrategy;
    private final InventoryLockStrategy inventoryLockStrategy;
    private final UserLockStrategy userLockStrategy;

    public CompositeLockPolicy(OrderLockStrategy orderLockStrategy,
                               InventoryLockStrategy inventoryLockStrategy,
                               UserLockStrategy userLockStrategy) {
        this.orderLockStrategy = orderLockStrategy;
        this.inventoryLockStrategy = inventoryLockStrategy;
        this.userLockStrategy = userLockStrategy;
    }

    @Override
    public <T> T executeWithLock(String orderId, List<String> skuIds, String userId,
                                 Callable<T> task, long waitTime, long leaseTime) throws Exception {
        long wait = waitTime <= 0 ? DEFAULT_WAIT_SEC : waitTime;
        List<LockKey> acquired = new ArrayList<>();
        try {
            if (orderId != null && !orderId.isBlank()) {
                if (!orderLockStrategy.tryLock(orderId, wait, leaseTime <= 0 ? 30 : leaseTime)) {
                    throw new IllegalStateException("Failed to acquire order lock: " + orderId);
                }
                acquired.add(new LockKey("order", orderId, null, null));
            }
            List<String> skuList = skuIds != null ? skuIds : Collections.emptyList();
            for (String skuId : skuList) {
                if (!inventoryLockStrategy.tryLock(skuId, "default", wait, leaseTime <= 0 ? 10 : leaseTime)) {
                    throw new IllegalStateException("Failed to acquire inventory lock: " + skuId);
                }
                acquired.add(new LockKey("inventory", skuId, "default", null));
            }
            if (userId != null && !userId.isBlank()) {
                if (!userLockStrategy.tryLock(userId, wait, leaseTime <= 0 ? 5 : leaseTime)) {
                    throw new IllegalStateException("Failed to acquire user lock: " + userId);
                }
                acquired.add(new LockKey("user", userId, null, null));
            }
            return task.call();
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                LockKey lk = acquired.get(i);
                switch (lk.scope) {
                    case "order" -> orderLockStrategy.unlock(lk.key1);
                    case "inventory" -> inventoryLockStrategy.unlock(lk.key1, lk.key2);
                    case "user" -> userLockStrategy.unlock(lk.key1);
                }
            }
        }
    }

    private record LockKey(String scope, String key1, String key2, String key3) {}
}
