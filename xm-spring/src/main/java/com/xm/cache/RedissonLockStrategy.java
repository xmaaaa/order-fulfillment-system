package com.xm.cache;

import com.xm.scenario.concurrent.lock.LockStrategy;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import java.util.concurrent.TimeUnit;

/**
 * 框架用：分布式锁策略，底层 Redisson，多机有效。
 * 配置 xm.scenario.lock=redisson 且 redis.enabled=true 时生效。
 */
public class RedissonLockStrategy implements LockStrategy {

    private final RedissonClient redissonClient;
    private final String lockType;
    private final String keyPrefix;

    /** 订单维锁，keyPrefix=order: */
    public static RedissonLockStrategy orderLock(RedissonClient redissonClient) {
        return new RedissonLockStrategy(redissonClient, "ORDER", "order:");
    }

    /** 库存维锁，keyPrefix=inventory: */
    public static RedissonLockStrategy inventoryLock(RedissonClient redissonClient) {
        return new RedissonLockStrategy(redissonClient, "INVENTORY", "inventory:");
    }

    /** 用户维锁，keyPrefix=user: */
    public static RedissonLockStrategy userLock(RedissonClient redissonClient) {
        return new RedissonLockStrategy(redissonClient, "USER", "user:");
    }

    /** 无前缀，供 Delegating*LockStrategy 使用（由 Delegating 加 prefix） */
    public static RedissonLockStrategy raw(RedissonClient redissonClient) {
        return new RedissonLockStrategy(redissonClient, "RAW", "");
    }

    public RedissonLockStrategy(RedissonClient redissonClient, String lockType, String keyPrefix) {
        this.redissonClient = redissonClient;
        this.lockType = lockType;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }

    @Override
    public String getLockType() {
        return lockType;
    }

    @Override
    public boolean tryLock(String resourceKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        RLock lock = redissonClient.getLock(keyPrefix + resourceKey);
        return lock.tryLock(waitTime, leaseTime <= 0 ? -1 : leaseTime, unit);
    }

    @Override
    public void unlock(String resourceKey) {
        RLock lock = redissonClient.getLock(keyPrefix + resourceKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public boolean isHeldByCurrentThread(String resourceKey) {
        RLock lock = redissonClient.getLock(keyPrefix + resourceKey);
        return lock.isHeldByCurrentThread();
    }
}
