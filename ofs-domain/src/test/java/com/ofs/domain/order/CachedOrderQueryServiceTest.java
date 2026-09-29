package com.ofs.domain.order;

import com.ofs.domain.concurrent.lock.LockStrategy;
import com.ofs.domain.concurrent.lock.delegate.InMemoryLockStrategy;
import com.ofs.domain.order.application.command.OrderCommandService;
import com.ofs.domain.order.application.command.OrderCommandServiceImpl;
import com.ofs.domain.order.application.query.CachedOrderQueryService;
import com.ofs.domain.order.application.query.InMemoryOrderViewCache;
import com.ofs.domain.order.application.query.OrderCacheSettings;
import com.ofs.domain.order.application.query.OrderCacheStats;
import com.ofs.domain.order.application.query.OrderIdFilter;
import com.ofs.domain.order.application.query.OrderQueryService;
import com.ofs.domain.order.application.query.OrderQueryServiceImpl;
import com.ofs.domain.order.application.query.OrderView;
import com.ofs.domain.order.application.query.OrderViewCache;
import com.ofs.domain.order.domain.model.OrderId;
import com.ofs.domain.order.domain.model.OrderRepository;
import com.ofs.domain.order.domain.service.OrderDomainService;
import com.ofs.domain.order.domain.state.OrderState;
import com.ofs.domain.order.infrastructure.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 读侧 Cache-Aside 装饰器的行为测试。全部用内存实现，不需要 Redis。
 */
class CachedOrderQueryServiceTest {

    private OrderRepository repo;
    private OrderCommandService commandService;
    private CountingQueryService counting;
    private InMemoryOrderViewCache cache;
    private OrderCacheStats stats;

    @BeforeEach
    void setUp() {
        repo = new InMemoryOrderRepository();
        commandService = new OrderCommandServiceImpl(new OrderDomainService(repo));
        counting = new CountingQueryService(new OrderQueryServiceImpl(repo));
        cache = new InMemoryOrderViewCache();
        stats = new OrderCacheStats();
    }

    private OrderId newOrder() {
        return commandService.createDraft("user-1", List.of(
                new OrderCommandService.OrderLineDto("SKU-1", 2, new BigDecimal("50.00"))
        ));
    }

    private CachedOrderQueryService cached(OrderIdFilter filter, LockStrategy lock, OrderCacheSettings settings) {
        return new CachedOrderQueryService(counting, cache, settings, stats, filter, lock);
    }

    private CachedOrderQueryService cached() {
        return cached(null, null, OrderCacheSettings.defaults());
    }

    // ---------- 1. 基础 Cache-Aside ----------

    @Test
    void firstReadMissesAndLoads_secondReadHitsCache() {
        OrderId id = newOrder();
        CachedOrderQueryService service = cached();

        OrderView first = service.getById(id).orElseThrow();
        assertEquals(OrderState.DRAFT, first.state());
        assertEquals(1, counting.count(), "首次读应回源查库一次");
        assertEquals(1, stats.misses());
        assertEquals(1, stats.loads());

        OrderView second = service.getById(id).orElseThrow();
        assertEquals(first, second);
        assertEquals(1, counting.count(), "第二次读应命中缓存，不再查库");
        assertEquals(1, stats.hits());
    }

    /**
     * 回归测试：开了重建锁之后，一次冷读会经过「首查 miss → 拿锁 → 双重检查」两次查缓存，
     * 但命中率计数必须只记一次 miss，否则命中率永远算不对（实测时发现过这个问题）。
     */
    @Test
    void singleColdReadRecordsExactlyOneMiss_evenWithRebuildLock() {
        OrderId id = newOrder();
        CachedOrderQueryService service = cached(null,
                new InMemoryLockStrategy("CACHE_REBUILD"), OrderCacheSettings.defaults());

        service.getById(id);

        assertEquals(1, stats.misses(), "一次冷读只该记一次 miss");
        assertEquals(1, stats.loads());
        assertEquals(0, stats.hits());
        assertEquals(0, stats.rebuildSkipped(), "无并发时双重检查也是 miss，不该算省下重建");

        service.getById(id);
        assertEquals(1, stats.hits());
        assertEquals(1, stats.misses());
        assertEquals(0.5d, stats.hitRate(), 0.0001);
    }

    @Test
    void expiredEntryIsReloaded() throws InterruptedException {
        OrderId id = newOrder();
        // ttl=20ms、无抖动，方便观察过期后重新回源
        CachedOrderQueryService service = cached(null, null,
                new OrderCacheSettings(20L, 20L, 0L, 200L, 3_000L));

        service.getById(id);
        assertEquals(1, counting.count());

        Thread.sleep(40);

        service.getById(id);
        assertEquals(2, counting.count(), "TTL 过期后应重新查库");
    }

    // ---------- 2. 空值缓存（防穿透第二道） ----------

    @Test
    void absentResultIsCached_soRepeatedMissesDoNotHitDb() {
        CachedOrderQueryService service = cached();

        assertTrue(service.getById("ORD-does-not-exist").isEmpty());
        assertEquals(1, counting.count());

        // 再查同一个不存在的 ID：应命中「不存在」占位，不再查库
        assertTrue(service.getById("ORD-does-not-exist").isEmpty());
        assertEquals(1, counting.count(), "空值缓存应挡住第二次查库");
        assertEquals(1, stats.absentHits());
    }

    @Test
    void absentCachingCanBeDisabledWithZeroTtl() {
        CachedOrderQueryService service = cached(null, null,
                new OrderCacheSettings(300_000L, 0L, 0L, 200L, 3_000L));

        service.getById("ORD-nope");
        service.getById("ORD-nope");
        assertEquals(2, counting.count(), "absentTtl=0 时不缓存空值，每次都查库");
        assertEquals(0, stats.absentHits());
    }

    // ---------- 3. 防穿透（存在性过滤器） ----------

    @Test
    void filterShortCircuitsUnknownId_withoutTouchingDb() {
        OrderIdFilter rejectAll = new OrderIdFilter() {
            @Override
            public void add(String orderId) {
            }

            @Override
            public boolean mightExist(String orderId) {
                return false;
            }
        };
        CachedOrderQueryService service = cached(rejectAll, null, OrderCacheSettings.defaults());

        assertTrue(service.getById("ORD-anything").isEmpty());
        assertEquals(0, counting.count(), "过滤器说一定不存在时，连库都不该查");
        assertEquals(1, stats.filtered());
    }

    @Test
    void filterFailureLetsRequestThrough() {
        OrderId id = newOrder();
        OrderIdFilter broken = new OrderIdFilter() {
            @Override
            public void add(String orderId) {
                throw new IllegalStateException("filter down");
            }

            @Override
            public boolean mightExist(String orderId) {
                throw new IllegalStateException("filter down");
            }
        };
        CachedOrderQueryService service = cached(broken, null, OrderCacheSettings.defaults());

        // 过滤器挂了必须放过，绝不能把真实订单判成不存在
        assertTrue(service.getById(id).isPresent());
        assertEquals(1, stats.cacheErrors());
    }

    // ---------- 4. 防击穿（重建锁） ----------

    @Test
    void concurrentMissesOnSameKeyLoadOnlyOnce() throws InterruptedException {
        OrderId id = newOrder();
        counting.delayMs(50);   // 让重建慢一点，制造并发窗口
        LockStrategy lock = new InMemoryLockStrategy("CACHE_REBUILD");
        // lockWait 给足，让没抢到锁的线程等到重建完成后走双重检查命中缓存
        CachedOrderQueryService service = cached(null, lock,
                new OrderCacheSettings(300_000L, 30_000L, 0L, 2_000L, 3_000L));

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Optional<OrderView>> results = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(service.getById(id));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "并发读应在 10s 内完成");
        pool.shutdownNow();

        assertEquals(threads, results.size());
        assertTrue(results.stream().allMatch(Optional::isPresent), "所有线程都应拿到订单");
        assertEquals(1, counting.count(),
                "防击穿：同一个 key 的并发 miss 只应回源一次，实际 " + counting.count());
        // lockWait 给足时，等待的线程最终都拿到了锁，然后靠「双重检查」发现已回填而直接返回——
        // 所以 lockMisses 应该是 0，rebuildSkipped 约等于线程数-1。这正是防击穿的理想路径：
        // 一个重建、其余等一下就拿到，谁都没打库，也没人被拒绝。
        assertEquals(0, stats.lockMisses());
        assertTrue(stats.rebuildSkipped() >= threads - 1,
                "双重检查应让其余线程省掉重建，实际 rebuildSkipped=" + stats.rebuildSkipped());
        // 每个请求只记一个命中/未命中结果，所以 20 个并发冷读恰好 20 次 miss，不是 40 次
        assertEquals(threads, stats.misses(), "命中率计数应每请求只记一次");
    }

    @Test
    void lockContentionFallsBackInsteadOfQueueing() throws InterruptedException {
        OrderId id = newOrder();
        counting.delayMs(100);
        LockStrategy lock = new InMemoryLockStrategy("CACHE_REBUILD");
        // lockWait=0：抢不到锁就立刻走「重读缓存 → 还没有就降级查库」，不排队等
        CachedOrderQueryService service = cached(null, lock,
                new OrderCacheSettings(300_000L, 30_000L, 0L, 0L, 3_000L));

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.getById(id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertTrue(stats.lockMisses() > 0, "应有线程没抢到锁");
        // 没抢到锁的线程宁可自己查一次库，也不阻塞在锁上——可用性优先于命中率
        assertTrue(stats.degradedLoads() > 0, "没抢到锁且缓存未回填时应降级直查库");
    }

    @Test
    void withoutRebuildLockAllConcurrentMissesHitDb() throws InterruptedException {
        OrderId id = newOrder();
        counting.delayMs(30);
        CachedOrderQueryService service = cached();   // 无锁

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.getById(id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        // 对照组：不开锁时并发 miss 会一起打库，这就是「击穿」
        assertTrue(counting.count() > 1,
                "不开重建锁时并发 miss 应该都打到库上（对照组），实际 " + counting.count());
    }

    // ---------- 5. 缓存故障降级 ----------

    @Test
    void cacheFailureDegradesToRepository() {
        OrderId id = newOrder();
        OrderViewCache broken = new OrderViewCache() {
            @Override
            public Optional<Entry> get(String orderId) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public void put(String orderId, OrderView view, long ttlMs) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public void putAbsent(String orderId, long ttlMs) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public void evict(String orderId) {
                throw new IllegalStateException("redis down");
            }
        };
        CachedOrderQueryService service = new CachedOrderQueryService(
                counting, broken, OrderCacheSettings.defaults(), stats, null, null);

        // Redis 全挂也必须能查到订单——可用性优先于命中率
        OrderView view = service.getById(id).orElseThrow();
        assertEquals(OrderState.DRAFT, view.state());
        assertTrue(stats.cacheErrors() >= 2, "读失败 + 回填失败各记一次");
    }

    @Test
    void lockFailureDegradesToRepository() {
        OrderId id = newOrder();
        LockStrategy brokenLock = new LockStrategy() {
            @Override
            public String getLockType() {
                return "BROKEN";
            }

            @Override
            public boolean tryLock(String resourceKey, long waitTime, long leaseTime, TimeUnit unit) {
                throw new IllegalStateException("lock service down");
            }

            @Override
            public void unlock(String resourceKey) {
            }

            @Override
            public boolean isHeldByCurrentThread(String resourceKey) {
                return false;
            }
        };
        CachedOrderQueryService service = cached(null, brokenLock, OrderCacheSettings.defaults());

        assertTrue(service.getById(id).isPresent(), "锁服务挂了也要能查到订单");
        assertEquals(1, stats.degradedLoads());
    }

    // ---------- 6. 边界 ----------

    @Test
    void blankIdReturnsEmptyWithoutTouchingCacheOrDb() {
        CachedOrderQueryService service = cached();
        assertTrue(service.getById("").isEmpty());
        assertTrue(service.getById("   ").isEmpty());
        assertTrue(service.getById((String) null).isEmpty());
        assertTrue(service.getById((OrderId) null).isEmpty());
        assertEquals(0, counting.count());
    }

    @Test
    void listByUserIdIsNotCached() {
        CachedOrderQueryService service = cached();
        assertTrue(service.listByUserId("user-1", 10).isEmpty());
        assertEquals(0, stats.hits());
        assertEquals(0, stats.misses(), "列表查询不走缓存，不该产生命中/未命中打点");
    }

    @Test
    void hitRateCountsAbsentHitsAsHits() {
        OrderId id = newOrder();
        CachedOrderQueryService service = cached();

        service.getById(id);            // miss
        service.getById(id);            // hit
        service.getById("ORD-none");    // miss
        service.getById("ORD-none");    // absent hit

        assertEquals(0.5d, stats.hitRate(), 0.0001);
        assertFalse(stats.toString().isBlank());
    }

    // ---------- 测试替身 ----------

    /** 统计回源次数的读侧代理，可选加延迟以制造并发窗口 */
    private static final class CountingQueryService implements OrderQueryService {

        private final OrderQueryService delegate;
        private final AtomicInteger count = new AtomicInteger();
        private volatile long delayMs = 0;

        CountingQueryService(OrderQueryService delegate) {
            this.delegate = delegate;
        }

        void delayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        int count() {
            return count.get();
        }

        @Override
        public Optional<OrderView> getById(OrderId orderId) {
            return getById(orderId.getValue());
        }

        @Override
        public Optional<OrderView> getById(String orderId) {
            count.incrementAndGet();
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.getById(orderId);
        }

        @Override
        public List<OrderView> listByUserId(String userId, int limit) {
            return delegate.listByUserId(userId, limit);
        }
    }
}
