package com.ofs.domain.order.application.query;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单缓存打点。ofs-domain 不依赖 Micrometer，这里只负责累加，
 * 由 ofs-app 的 {@code OrderCacheConfig} 绑成 Prometheus Gauge。
 *
 * <p>看板上真正有用的是三件事：命中率（够不够本）、degradedLoads（缓存是不是在裸奔）、
 * cacheErrors（Redis 有没有在抖）。
 */
public class OrderCacheStats {

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong absentHits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong loads = new AtomicLong();
    private final AtomicLong filtered = new AtomicLong();
    private final AtomicLong lockMisses = new AtomicLong();
    private final AtomicLong rebuildSkipped = new AtomicLong();
    private final AtomicLong degradedLoads = new AtomicLong();
    private final AtomicLong cacheErrors = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    /** 命中且有值 */
    public void recordHit() {
        hits.incrementAndGet();
    }

    /** 命中「不存在」占位（空值缓存生效，省掉一次查库） */
    public void recordAbsentHit() {
        absentHits.incrementAndGet();
    }

    /** 未命中 */
    public void recordMiss() {
        misses.incrementAndGet();
    }

    /** 回源查库次数 */
    public void recordLoad() {
        loads.incrementAndGet();
    }

    /** 被存在性过滤器短路（防穿透生效，连库都没查） */
    public void recordFiltered() {
        filtered.incrementAndGet();
    }

    /** 未抢到重建锁（说明有并发热点，防击穿正在起作用） */
    public void recordLockMiss() {
        lockMisses.incrementAndGet();
    }

    /**
     * 双重检查/重试时发现别人已经回填好了，省掉一次重建。
     * 这是防击穿真正省下的查库次数——热点 key 上并发越高，这个数越接近「并发数 - 1」。
     */
    public void recordRebuildSkipped() {
        rebuildSkipped.incrementAndGet();
    }

    /** 降级：没抢到锁且缓存仍未回填，直接查库 */
    public void recordDegradedLoad() {
        degradedLoads.incrementAndGet();
    }

    /** 缓存组件抛异常（读/写/失效任一环节） */
    public void recordCacheError() {
        cacheErrors.incrementAndGet();
    }

    /** 写操作触发的失效 */
    public void recordEviction() {
        evictions.incrementAndGet();
    }

    public long hits() {
        return hits.get();
    }

    public long absentHits() {
        return absentHits.get();
    }

    public long misses() {
        return misses.get();
    }

    public long loads() {
        return loads.get();
    }

    public long filtered() {
        return filtered.get();
    }

    public long lockMisses() {
        return lockMisses.get();
    }

    public long rebuildSkipped() {
        return rebuildSkipped.get();
    }

    public long degradedLoads() {
        return degradedLoads.get();
    }

    public long cacheErrors() {
        return cacheErrors.get();
    }

    public long evictions() {
        return evictions.get();
    }

    /**
     * 命中率。hits/absentHits/misses 三者<b>每个请求只记一次</b>（记的是第一次查缓存的结果），
     * 所以分母就是「查过缓存的请求数」，这个比值可以直接当命中率读。
     * 空值命中算命中——它同样省掉了一次查库。被过滤器短路的请求不计入：那些根本没走到缓存。
     */
    public double hitRate() {
        long hit = hits.get() + absentHits.get();
        long total = hit + misses.get();
        return total == 0 ? 0d : (double) hit / total;
    }

    @Override
    public String toString() {
        return ("OrderCacheStats{hits=%d, absentHits=%d, misses=%d, loads=%d, filtered=%d, "
                + "lockMisses=%d, rebuildSkipped=%d, degraded=%d, errors=%d, evictions=%d, hitRate=%.3f}")
                .formatted(hits.get(), absentHits.get(), misses.get(), loads.get(), filtered.get(),
                        lockMisses.get(), rebuildSkipped.get(), degradedLoads.get(), cacheErrors.get(),
                        evictions.get(), hitRate());
    }
}
