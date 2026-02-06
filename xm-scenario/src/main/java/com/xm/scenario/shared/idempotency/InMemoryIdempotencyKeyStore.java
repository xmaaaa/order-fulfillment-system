package com.xm.scenario.shared.idempotency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存幂等键存储（单机/单测）。生产用 Redis SET key NX PX ttl 或 DB 唯一索引 + 插入。
 */
public class InMemoryIdempotencyKeyStore implements IdempotencyKeyStore {

    private final Map<String, Long> store = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, long ttlMs) {
        long now = System.currentTimeMillis();
        Long expireAt = store.get(key);
        if (expireAt != null && expireAt > now) {
            return false;
        }
        store.put(key, now + ttlMs);
        return true;
    }

    @Override
    public void release(String key) {
        store.remove(key);
    }
}
