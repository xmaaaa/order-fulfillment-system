package com.ofs.domain.order.application.query;

import com.ofs.domain.concurrent.lock.LockStrategy;
import com.ofs.domain.order.application.query.OrderViewCache.Entry;
import com.ofs.domain.order.domain.model.OrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 读侧 Cache-Aside 装饰器。与写侧那串装饰器同构：包一层、不改被包者。
 *
 * <p>读路径（{@code getById}）依次过四道：
 * <ol>
 *   <li><b>查缓存</b>——命中值直接返回；命中「不存在」占位也直接返回 empty，不查库。</li>
 *   <li><b>防穿透</b>——存在性过滤器（布隆）说一定不存在，直接短路。挡的是「拿海量不存在的 ID
 *       猛打接口」这种打法：缓存永远不命中，请求全落库。第二道防线是上面的空值缓存。</li>
 *   <li><b>防击穿</b>——热点 key 刚过期时会有一大波请求同时 miss 并涌向数据库。这里用分布式锁
 *       让同一订单只放一个重建者进去，拿到锁后还要<b>双重检查</b>缓存（等锁期间别人可能已回填）。</li>
 *   <li><b>回源 + 回填</b>——TTL 加随机抖动防雪崩，见 {@link OrderCacheSettings#jitterMs()}。</li>
 * </ol>
 *
 * <p><b>可用性优先于命中率</b>：缓存或锁组件抛异常一律当「未命中」处理并降级读库
 * （只打点，不抛出）。Redis 挂了要能继续卖货，不能连查询一起挂。同理，没抢到锁的请求
 * 不会一直等——重试读一次缓存，还没有就直接查库。
 *
 * <p>写侧的失效在 {@link com.ofs.domain.order.infrastructure.repository.CacheEvictingOrderRepository}——
 * 挂在仓储层是为了覆盖所有写路径，并通过 {@code AfterCommitExecutor} 推迟到事务提交后。
 */
public class CachedOrderQueryService implements OrderQueryService {

    private static final Logger log = LoggerFactory.getLogger(CachedOrderQueryService.class);

    private final OrderQueryService delegate;
    private final OrderViewCache cache;
    private final OrderCacheSettings settings;
    private final OrderCacheStats stats;

    /** 可空：为 null 表示不启用防穿透过滤器（默认，见 {@link OrderIdFilter} 里的预热前提） */
    private final OrderIdFilter idFilter;

    /**
     * 可空：为 null 表示不启用防击穿锁（单机低并发场景没必要）。
     * <p>注意用的是独立 key 前缀 {@code cache:order:}，<b>不能</b>复用业务订单锁——
     * 否则一个查询在重建缓存时会把同订单的写操作挡住，读拖累写。
     */
    private final LockStrategy rebuildLock;

    private static final String REBUILD_LOCK_PREFIX = "cache:order:";

    public CachedOrderQueryService(OrderQueryService delegate,
                                   OrderViewCache cache,
                                   OrderCacheSettings settings,
                                   OrderCacheStats stats,
                                   OrderIdFilter idFilter,
                                   LockStrategy rebuildLock) {
        this.delegate = delegate;
        this.cache = cache;
        this.settings = settings == null ? OrderCacheSettings.defaults() : settings;
        this.stats = stats == null ? new OrderCacheStats() : stats;
        this.idFilter = idFilter;
        this.rebuildLock = rebuildLock;
    }

    /** 最简装配：只做 Cache-Aside + 空值缓存，不开防穿透与防击穿 */
    public CachedOrderQueryService(OrderQueryService delegate, OrderViewCache cache) {
        this(delegate, cache, OrderCacheSettings.defaults(), new OrderCacheStats(), null, null);
    }

    public OrderCacheStats stats() {
        return stats;
    }

    @Override
    public Optional<OrderView> getById(OrderId orderId) {
        return orderId == null ? Optional.empty() : getById(orderId.getValue());
    }

    @Override
    public Optional<OrderView> getById(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }

        // 1. 查缓存（这一次决定本请求算命中还是未命中）
        Entry hit = lookup(orderId, true);
        if (hit != null) {
            return toResult(hit);
        }

        // 2. 防穿透：过滤器说「一定不存在」，连库都不用查
        if (idFilter != null && !mightExist(orderId)) {
            stats.recordFiltered();
            return Optional.empty();
        }

        // 3./4. 重建（有锁走防击穿，无锁直接回源）
        return rebuildLock == null ? loadAndFill(orderId) : loadUnderLock(orderId);
    }

    /**
     * 列表查询不缓存：列表缓存的失效面太大（任一订单变更都要失效所有含它的列表页），
     * 收益却低（列表通常带分页/筛选，命中率差）。生产要做就走读库或 ES 投影，别硬塞 Redis。
     */
    @Override
    public List<OrderView> listByUserId(String userId, int limit) {
        return delegate.listByUserId(userId, limit);
    }

    // ---------- 内部 ----------

    /**
     * 查缓存并打点。命中率相关的三个计数器（hits/absentHits/misses）<b>每个请求只记一次</b>，
     * 记的是第一次查缓存的结果；后续的双重检查/重试传 {@code countOutcome=false}，
     * 否则一次冷读会记两次 miss，命中率就没法直接读了。
     * 双重检查省下的重建另记在 {@link OrderCacheStats#recordRebuildSkipped()}。
     *
     * @return 命中返回 Entry（{@code isAbsent()} 为 true 表示命中「不存在」占位）；未命中返回 null
     */
    private Entry lookup(String orderId, boolean countOutcome) {
        Optional<Entry> cached;
        try {
            cached = cache.get(orderId);
        } catch (RuntimeException ex) {
            // 缓存读失败 → 当未命中，降级读库
            stats.recordCacheError();
            if (countOutcome) {
                stats.recordMiss();
            }
            log.warn("Cache read failed for orderId={}, falling back to repository: {}", orderId, ex.getMessage());
            return null;
        }
        if (cached == null || cached.isEmpty()) {
            if (countOutcome) {
                stats.recordMiss();
            }
            return null;
        }
        Entry entry = cached.get();
        if (countOutcome) {
            if (entry.isAbsent()) {
                stats.recordAbsentHit();
            } else {
                stats.recordHit();
            }
        } else {
            // 重建前的双重检查命中：别人已经回填好了，本次省掉一次查库
            stats.recordRebuildSkipped();
        }
        return entry;
    }

    private static Optional<OrderView> toResult(Entry entry) {
        return Optional.ofNullable(entry.view());
    }

    /** 过滤器故障时返回 true（放过），绝不能因为过滤器挂了就把订单判成不存在 */
    private boolean mightExist(String orderId) {
        try {
            return idFilter.mightExist(orderId);
        } catch (RuntimeException ex) {
            stats.recordCacheError();
            log.warn("OrderIdFilter check failed for orderId={}, letting it through: {}", orderId, ex.getMessage());
            return true;
        }
    }

    private Optional<OrderView> loadUnderLock(String orderId) {
        String lockKey = REBUILD_LOCK_PREFIX + orderId;
        boolean locked = tryLock(lockKey);

        if (!locked) {
            stats.recordLockMiss();
            // 没抢到锁说明别人正在重建。再读一次缓存，多数情况这时已经回填好了
            Entry retry = lookup(orderId, false);
            if (retry != null) {
                return toResult(retry);
            }
            // 仍未回填：宁可多打一次库，也不让请求排队等锁（可用性优先于命中率）
            stats.recordDegradedLoad();
            return load(orderId);
        }

        try {
            // 双重检查：等锁这段时间里可能已经被别人回填了
            Entry recheck = lookup(orderId, false);
            if (recheck != null) {
                return toResult(recheck);
            }
            return loadAndFill(orderId);
        } finally {
            unlock(lockKey);
        }
    }

    private boolean tryLock(String lockKey) {
        try {
            return rebuildLock.tryLock(lockKey, settings.lockWaitMs(), settings.lockLeaseMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            // 恢复中断标记交给上层处理，本次按「没拿到锁」降级
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ex) {
            stats.recordCacheError();
            return false;
        }
    }

    private void unlock(String lockKey) {
        try {
            rebuildLock.unlock(lockKey);
        } catch (RuntimeException ex) {
            // 解锁失败只会让这把锁等到 lease 到期，不影响本次结果
            stats.recordCacheError();
        }
    }

    /** 回源查库并回填缓存 */
    private Optional<OrderView> loadAndFill(String orderId) {
        Optional<OrderView> loaded = load(orderId);
        try {
            if (loaded.isPresent()) {
                cache.put(orderId, loaded.get(), ttlWithJitter());
            } else if (settings.absentTtlMs() > 0) {
                // 空值缓存：不存在的 ID 也占个短命位置，防止被反复拿来打库
                cache.putAbsent(orderId, settings.absentTtlMs());
            }
        } catch (RuntimeException ex) {
            // 回填失败只影响下次命中率，本次结果已经拿到了
            stats.recordCacheError();
            log.warn("Cache fill failed for orderId={}: {}", orderId, ex.getMessage());
        }
        return loaded;
    }

    private Optional<OrderView> load(String orderId) {
        stats.recordLoad();
        return delegate.getById(orderId);
    }

    private long ttlWithJitter() {
        long jitter = settings.jitterMs();
        return jitter == 0 ? settings.ttlMs() : settings.ttlMs() + ThreadLocalRandom.current().nextLong(jitter);
    }
}
