package com.ofs.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 订单缓存（Cache-Aside）参数。开关是 {@code ofs.scenario.cache}（none|memory|redis），
 * 这里只管参数。语义见 {@link com.ofs.domain.order.application.query.OrderCacheSettings}。
 */
@ConfigurationProperties(prefix = "ofs.scenario.order-cache")
public class OrderCacheProperties {

    /** 正常值 TTL */
    private Duration ttl = Duration.ofMinutes(5);

    /** 「不存在」占位 TTL；设为 0 关闭空值缓存。远短于 ttl，否则刚建的单会查不到 */
    private Duration absentTtl = Duration.ofSeconds(30);

    /** TTL 随机抖动上界，防雪崩；设为 0 关闭 */
    private Duration jitter = Duration.ofSeconds(30);

    /** 重建锁等待时间；等不到就降级直查库 */
    private Duration lockWait = Duration.ofMillis(200);

    /** 重建锁持有上限；必须大于「查库 + 回填」的最坏耗时 */
    private Duration lockLease = Duration.ofSeconds(3);

    /** 是否启用防击穿的重建锁 */
    private boolean rebuildLock = true;

    /** 缓存 key 前缀 */
    private String keyPrefix = "ofs:order:view:";

    private final BloomFilter bloomFilter = new BloomFilter();

    /**
     * 防穿透布隆过滤器。<b>默认关闭</b>：启用前必须把已存在的订单 ID 全量预热进去，
     * 否则历史订单会被误判成不存在。原因见
     * {@link com.ofs.domain.order.application.query.OrderIdFilter}。
     */
    public static class BloomFilter {

        private boolean enabled = false;

        private String key = "ofs:order:ids:bloom";

        /** 预期插入量。按「重建周期内的增量上限」配，不是按当前订单量 */
        private long expectedInsertions = 1_000_000L;

        /** 目标误判率（假阳性）。改这个值需要先删掉旧 key 才会生效 */
        private double falseProbability = 0.01d;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKey() {
            return key == null || key.isBlank() ? "ofs:order:ids:bloom" : key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public long getExpectedInsertions() {
            return expectedInsertions > 0 ? expectedInsertions : 1_000_000L;
        }

        public void setExpectedInsertions(long expectedInsertions) {
            this.expectedInsertions = expectedInsertions;
        }

        public double getFalseProbability() {
            return falseProbability > 0 && falseProbability < 1 ? falseProbability : 0.01d;
        }

        public void setFalseProbability(double falseProbability) {
            this.falseProbability = falseProbability;
        }
    }

    public Duration getTtl() {
        return isPositive(ttl) ? ttl : Duration.ofMinutes(5);
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getAbsentTtl() {
        return absentTtl != null && !absentTtl.isNegative() ? absentTtl : Duration.ofSeconds(30);
    }

    public void setAbsentTtl(Duration absentTtl) {
        this.absentTtl = absentTtl;
    }

    public Duration getJitter() {
        return jitter != null && !jitter.isNegative() ? jitter : Duration.ZERO;
    }

    public void setJitter(Duration jitter) {
        this.jitter = jitter;
    }

    public Duration getLockWait() {
        return lockWait != null && !lockWait.isNegative() ? lockWait : Duration.ofMillis(200);
    }

    public void setLockWait(Duration lockWait) {
        this.lockWait = lockWait;
    }

    public Duration getLockLease() {
        return isPositive(lockLease) ? lockLease : Duration.ofSeconds(3);
    }

    public void setLockLease(Duration lockLease) {
        this.lockLease = lockLease;
    }

    public boolean isRebuildLock() {
        return rebuildLock;
    }

    public void setRebuildLock(boolean rebuildLock) {
        this.rebuildLock = rebuildLock;
    }

    public String getKeyPrefix() {
        return keyPrefix == null || keyPrefix.isBlank() ? "ofs:order:view:" : keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public BloomFilter getBloomFilter() {
        return bloomFilter;
    }

    private static boolean isPositive(Duration d) {
        return d != null && !d.isNegative() && !d.isZero();
    }
}
