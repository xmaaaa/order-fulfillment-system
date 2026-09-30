package com.ofs.domain.order;

import com.ofs.domain.order.application.query.CachedOrderQueryService;
import com.ofs.domain.order.application.query.InMemoryOrderViewCache;
import com.ofs.domain.order.application.query.OrderCacheSettings;
import com.ofs.domain.order.application.query.OrderCacheStats;
import com.ofs.domain.order.application.query.OrderIdFilter;
import com.ofs.domain.order.application.query.OrderQueryService;
import com.ofs.domain.order.application.query.OrderQueryServiceImpl;
import com.ofs.domain.order.domain.model.Order;
import com.ofs.domain.order.domain.model.OrderId;
import com.ofs.domain.order.domain.model.OrderLine;
import com.ofs.domain.order.domain.model.OrderRepository;
import com.ofs.domain.order.domain.service.OrderDomainService;
import com.ofs.domain.order.domain.state.OrderState;
import com.ofs.domain.order.infrastructure.repository.CacheEvictingOrderRepository;
import com.ofs.domain.order.infrastructure.repository.InMemoryOrderRepository;
import com.ofs.domain.shared.transaction.AfterCommitExecutor;
import com.ofs.domain.shared.transaction.ImmediateAfterCommitExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仓储层缓存失效装饰器。两件事各测一组：
 *
 * <ul>
 *   <li><b>覆盖面</b>——绕过 OrderCommandService 的写路径（TCC / Saga / 超时调度器都直接调
 *       OrderDomainService）也必须让缓存失效。这是失效挂在仓储层的理由。</li>
 *   <li><b>时序</b>——失效必须发生在事务<b>提交后</b>，不能在事务内就把缓存删了。
 *       这是它走 {@link AfterCommitExecutor} 而不是当场删的理由。</li>
 * </ul>
 */
class CacheEvictingOrderRepositoryTest {

    private InMemoryOrderViewCache cache;
    private OrderCacheStats stats;
    private OrderRepository repo;
    private OrderDomainService domainService;
    private OrderQueryService queryService;

    @BeforeEach
    void setUp() {
        cache = new InMemoryOrderViewCache();
        stats = new OrderCacheStats();
        // 默认用「立即执行」，等价于无事务环境：save 返回即可见，当场失效就是对的
        repo = newRepo(new ImmediateAfterCommitExecutor(), null);
        domainService = new OrderDomainService(repo);
        queryService = newQueryService(repo, null);
    }

    private OrderRepository newRepo(AfterCommitExecutor executor, OrderIdFilter filter) {
        return new CacheEvictingOrderRepository(
                new InMemoryOrderRepository(), cache, stats, filter, executor);
    }

    private OrderQueryService newQueryService(OrderRepository r, OrderIdFilter filter) {
        return new CachedOrderQueryService(
                new OrderQueryServiceImpl(r), cache, OrderCacheSettings.defaults(), stats, filter, null);
    }

    private OrderId newOrder(OrderDomainService svc) {
        return svc.createDraft("user-1", List.of(new OrderLine("SKU-1", 1, new BigDecimal("30.00"))));
    }

    // ---------- 一、覆盖面：绕过应用服务层的写也要失效 ----------

    /**
     * 这是把失效挂在仓储层的唯一理由：TCC / Saga / 超时调度器都走 OrderDomainService，
     * 只在 OrderCommandService 挂失效的话，这条路径写完缓存还是旧的。
     */
    @Test
    void writesBypassingCommandServiceStillEvict() {
        OrderId id = newOrder(domainService);

        assertEquals(OrderState.DRAFT, queryService.getById(id).orElseThrow().state());
        assertTrue(cache.get(id.getValue()).isPresent());

        // 模拟 TCC/Saga/调度器的写法：直接调领域服务，完全不经过 OrderCommandService
        domainService.submit(id);
        assertTrue(cache.get(id.getValue()).isEmpty(), "绕过应用服务的写也必须让缓存失效");
        assertEquals(OrderState.SUBMITTED, queryService.getById(id).orElseThrow().state());

        domainService.markPaid(id, "PAY-1");
        assertEquals(OrderState.PAID, queryService.getById(id).orElseThrow().state());

        domainService.ship(id);
        assertEquals(OrderState.SHIPPED, queryService.getById(id).orElseThrow().state(),
                "缓存若没失效，这里会读到 PAID");
    }

    @Test
    void schedulerStyleCancelEvicts() {
        OrderId id = newOrder(domainService);
        domainService.submit(id);
        queryService.getById(id);

        // OrderTimeoutScheduler 就是这么取消订单的
        domainService.cancel(id);
        assertEquals(OrderState.CANCELLED, queryService.getById(id).orElseThrow().state());
    }

    @Test
    void failedOptimisticLockDoesNotEvict() {
        OrderId id = newOrder(domainService);
        queryService.getById(id);
        long before = stats.evictions();

        Order stale = repo.findById(id);
        domainService.submit(id);                 // 真实推进一次，库里 version 已 +1
        long afterRealWrite = stats.evictions();
        assertEquals(before + 1, afterRealWrite);

        // 拿旧 version 再写：CAS 应失败，且不该触发失效
        assertFalse(repo.updateVersion(stale), "旧 version 的 CAS 应失败");
        assertEquals(afterRealWrite, stats.evictions(),
                "乐观锁冲突时库里没变，缓存里的值仍然有效，不该白删");
    }

    @Test
    void readsArePassedThrough() {
        OrderId id = newOrder(domainService);
        assertEquals(id.getValue(), repo.findById(id).getId().getValue());
        domainService.submit(id);
        assertTrue(repo.findSubmittedOrderIdsOlderThan(System.currentTimeMillis() + 1000).contains(id),
                "查询方法应原样透传给被装饰的仓储");
    }

    // ---------- 二、时序：失效必须发生在提交之后 ----------

    /**
     * 核心回归测试：仓储的 save/updateVersion 是在事务<b>内</b>调用的。
     * 若在那里当场删缓存，删完到提交之间进来的读会查到旧值并回填，脏到 TTL 到期。
     * 所以失效必须推迟到提交后。
     */
    @Test
    void evictionIsDeferredUntilCommit() {
        ManualAfterCommitExecutor tx = new ManualAfterCommitExecutor();
        OrderRepository txRepo = newRepo(tx, null);
        OrderDomainService svc = new OrderDomainService(txRepo);
        OrderQueryService qs = newQueryService(txRepo, null);

        OrderId id = newOrder(svc);
        tx.commit();                               // 建单提交

        assertEquals(OrderState.DRAFT, qs.getById(id).orElseThrow().state());
        assertTrue(cache.get(id.getValue()).isPresent(), "读完应已回填缓存");

        // 事务内写入：此时还没提交，缓存不能动
        svc.submit(id);
        assertTrue(cache.get(id.getValue()).isPresent(),
                "提交之前不该删缓存——否则这个窗口里的读会回填旧值");
        assertEquals(1, tx.pendingCount(), "失效动作应处于挂起状态");

        // 提交后才失效
        tx.commit();
        assertTrue(cache.get(id.getValue()).isEmpty(), "提交后才应失效");
        assertEquals(OrderState.SUBMITTED, qs.getById(id).orElseThrow().state());
    }

    /** 事务回滚：库没变，缓存里的值仍然是对的，不该失效 */
    @Test
    void rollbackDoesNotEvict() {
        ManualAfterCommitExecutor tx = new ManualAfterCommitExecutor();
        OrderRepository txRepo = newRepo(tx, null);
        OrderDomainService svc = new OrderDomainService(txRepo);
        OrderQueryService qs = newQueryService(txRepo, null);

        OrderId id = newOrder(svc);
        tx.commit();
        qs.getById(id);                            // 灌缓存
        long evictionsBefore = stats.evictions();

        svc.submit(id);
        tx.rollback();

        assertEquals(evictionsBefore, stats.evictions(), "回滚不该触发失效");
        assertTrue(cache.get(id.getValue()).isPresent(), "回滚后缓存里的旧值依然有效");
    }

    /** 无事务环境（本项目默认）：「提交后」就是「现在」，立即失效即正确 */
    @Test
    void withoutTransactionEvictsImmediately() {
        OrderId id = newOrder(domainService);
        queryService.getById(id);
        assertTrue(cache.get(id.getValue()).isPresent());

        domainService.submit(id);
        assertTrue(cache.get(id.getValue()).isEmpty(), "无事务时应当场失效，不需要等谁");
    }

    // ---------- 三、防穿透过滤器的登记 ----------

    @Test
    void newOrderIsRegisteredIntoFilter() {
        Set<String> registered = new HashSet<>();
        OrderIdFilter filter = recordingFilter(registered);
        OrderRepository guardedRepo = newRepo(new ImmediateAfterCommitExecutor(), filter);
        OrderDomainService svc = new OrderDomainService(guardedRepo);

        OrderId id = newOrder(svc);

        // 不登记的话，防穿透那道会把这张新单误判成不存在
        assertTrue(registered.contains(id.getValue()), "新建订单必须进存在性过滤器");
        assertTrue(newQueryService(guardedRepo, filter).getById(id).isPresent(),
                "登记过的新单应能正常查到");
    }

    /**
     * 登记也要等提交——事务回滚的话订单压根不存在，不该进过滤器。
     * 布隆过滤器删不掉元素，错误登记只能靠整体重建来清。
     */
    @Test
    void filterRegistrationIsAlsoDeferredAndSkippedOnRollback() {
        Set<String> registered = new HashSet<>();
        ManualAfterCommitExecutor tx = new ManualAfterCommitExecutor();
        OrderDomainService svc = new OrderDomainService(newRepo(tx, recordingFilter(registered)));

        OrderId rolledBack = newOrder(svc);
        assertTrue(registered.isEmpty(), "提交前不该登记");
        tx.rollback();
        assertFalse(registered.contains(rolledBack.getValue()), "回滚的订单不该进过滤器");

        OrderId committed = newOrder(svc);
        tx.commit();
        assertTrue(registered.contains(committed.getValue()), "提交后才登记");
    }

    @Test
    void filterRegistrationFailureDoesNotBreakTheWrite() {
        OrderIdFilter brokenFilter = new OrderIdFilter() {
            @Override
            public void add(String orderId) {
                throw new IllegalStateException("bloom down");
            }

            @Override
            public boolean mightExist(String orderId) {
                return true;
            }
        };
        InMemoryOrderRepository inner = new InMemoryOrderRepository();
        OrderDomainService svc = new OrderDomainService(new CacheEvictingOrderRepository(
                inner, cache, stats, brokenFilter, new ImmediateAfterCommitExecutor()));

        // 订单已落库，不能因为布隆过滤器写失败就把它回滚（代价是日志里那条 ERROR）
        OrderId id = newOrder(svc);
        assertEquals(OrderState.DRAFT, new OrderQueryServiceImpl(inner).getById(id).orElseThrow().state());
        assertTrue(stats.cacheErrors() >= 1);
    }

    // ---------- 测试替身 ----------

    private static OrderIdFilter recordingFilter(Set<String> sink) {
        return new OrderIdFilter() {
            @Override
            public void add(String orderId) {
                sink.add(orderId);
            }

            @Override
            public boolean mightExist(String orderId) {
                return sink.contains(orderId);
            }
        };
    }

    /** 手动控制提交/回滚的执行器，用来观察「挂起 → 提交才执行」这个时序 */
    private static final class ManualAfterCommitExecutor implements AfterCommitExecutor {

        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void afterCommit(Runnable action) {
            pending.add(action);
        }

        void commit() {
            List<Runnable> toRun = List.copyOf(pending);
            pending.clear();
            toRun.forEach(Runnable::run);
        }

        void rollback() {
            pending.clear();
        }

        int pendingCount() {
            return pending.size();
        }
    }
}
