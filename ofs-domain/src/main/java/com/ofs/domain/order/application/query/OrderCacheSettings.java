package com.ofs.domain.order.application.query;

/**
 * 订单缓存参数。
 *
 * @param ttlMs       正常值的存活时间
 * @param absentTtlMs 「不存在」占位的存活时间；应远短于 ttlMs——占位太久会让「刚建的单查不到」。
 *                    设为 0 表示不做空值缓存
 * @param jitterMs    TTL 随机抖动上界，实际 TTL = ttlMs + random[0, jitterMs)。
 *                    <b>防缓存雪崩</b>：批量回填（如冷启动预热、大促前刷缓存）的 key 若 TTL 完全相同，
 *                    会在同一秒集体过期把库打穿。设为 0 表示不抖动
 * @param lockWaitMs  重建锁的等待时间。宁可短——等不到就降级直查库，也不让请求堆在锁上
 * @param lockLeaseMs 重建锁的持有上限，必须大于「查库 + 回填」的最坏耗时，否则锁提前释放，防击穿失效
 */
public record OrderCacheSettings(long ttlMs, long absentTtlMs, long jitterMs, long lockWaitMs, long lockLeaseMs) {

    public OrderCacheSettings {
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("ttlMs must be positive, got " + ttlMs);
        }
        if (absentTtlMs < 0 || jitterMs < 0 || lockWaitMs < 0 || lockLeaseMs < 0) {
            throw new IllegalArgumentException("cache durations must not be negative");
        }
    }

    public static OrderCacheSettings defaults() {
        return new OrderCacheSettings(300_000L, 30_000L, 30_000L, 200L, 3_000L);
    }
}
