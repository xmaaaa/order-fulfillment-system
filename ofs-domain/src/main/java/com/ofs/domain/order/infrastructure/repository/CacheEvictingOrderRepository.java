package com.ofs.domain.order.infrastructure.repository;

import com.ofs.domain.order.application.query.OrderCacheStats;
import com.ofs.domain.order.application.query.OrderIdFilter;
import com.ofs.domain.order.application.query.OrderViewCache;
import com.ofs.domain.order.domain.model.Order;
import com.ofs.domain.order.domain.model.OrderId;
import com.ofs.domain.order.domain.model.OrderRepository;
import com.ofs.domain.shared.transaction.AfterCommitExecutor;
import com.ofs.domain.shared.transaction.ImmediateAfterCommitExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Cache-Aside 的写半边：订单写入后失效缓存。<b>失效在事务提交之后发生。</b>
 *
 * <p>这个类要同时满足两件事，缺一不可：
 *
 * <h3>① 覆盖完整——所以挂在仓储层</h3>
 * 订单的写入口不止一个。除了 {@code OrderCommandService}，还有
 * {@code OrderSubmitWithPaymentTccService}、{@code OrderSubmitWithPaymentSagaService}、
 * {@code OrderTimeoutScheduler} 以及 Seata 的 TCC/Saga Action——它们都直接调
 * {@code OrderDomainService}，绕过应用服务层。只在 OrderCommandService 上挂失效，
 * TCC 支付完再查订单就会读到旧状态。而所有写路径最终都收敛到 {@link OrderRepository#save}
 * 和 {@link OrderRepository#updateVersion}，挂在这里才是「一个都不漏」。
 *
 * <h3>② 时序正确——所以走 {@link AfterCommitExecutor}</h3>
 * 但仓储的 save/updateVersion 是在<b>事务内</b>被调用的。如果在这里当场删缓存，等于基于一个
 * 还没提交的事实发布副作用：删完到提交之间进来的读会查到旧值并回填，脏到 TTL 到期；事务万一
 * 回滚，更是白删。所以这里<b>不直接删</b>，而是把删的动作交给 {@code AfterCommitExecutor}——
 * 有事务就挂到提交后，没事务就立即执行（数据本来就已可见）。
 *
 * <p>这两条合起来，才既不漏路径、又不早于提交。早先的做法是「仓储层当场删 + 应用服务层再删一次」，
 * 用两个各有残缺的层互相打补丁；换成 after-commit 之后，应用服务层那一层就没有存在意义了，已删除。
 *
 * <h3>已知边界</h3>
 * 事务回滚时不失效（库没变，缓存本来就是对的）。唯一的例外是：如果某个写用例在<b>同一个事务内</b>
 * 又通过带缓存的读路径查了这笔订单，它会看到自己未提交的修改并回填进缓存，此时回滚就会留下
 * 一个「从未存在过」的值。本项目不存在这种用例——写路径读订单走的是
 * {@code OrderDomainService}/仓储，不经过 {@code CachedOrderQueryService}。若将来出现，
 * 把实现换成 {@code afterCompletion(status)} 并在回滚时也失效即可。
 *
 * <p>更彻底的演进方向（领域事件 / 订阅 binlog）见 {@code ofs-domain/docs/缓存-Cache-Aside.md}。
 */
public class CacheEvictingOrderRepository implements OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictingOrderRepository.class);

    private final OrderRepository delegate;
    private final OrderViewCache cache;
    private final OrderCacheStats stats;
    private final AfterCommitExecutor afterCommitExecutor;

    /** 可空：为 null 表示未启用防穿透过滤器 */
    private final OrderIdFilter idFilter;

    public CacheEvictingOrderRepository(OrderRepository delegate,
                                        OrderViewCache cache,
                                        OrderCacheStats stats,
                                        OrderIdFilter idFilter,
                                        AfterCommitExecutor afterCommitExecutor) {
        this.delegate = delegate;
        this.cache = cache;
        this.stats = stats == null ? new OrderCacheStats() : stats;
        this.idFilter = idFilter;
        this.afterCommitExecutor = afterCommitExecutor == null
                ? new ImmediateAfterCommitExecutor()
                : afterCommitExecutor;
    }

    @Override
    public void save(Order order) {
        delegate.save(order);
        if (order == null || order.getId() == null) {
            return;
        }
        String orderId = order.getId().getValue();
        // save 只在建单时调用：新 ID 必须进存在性过滤器，否则防穿透会把它误判成不存在。
        // 这个也挂到提交后——事务回滚的话订单压根不存在，不该登记进去（布隆过滤器还删不掉）
        afterCommitExecutor.afterCommit(() -> {
            register(orderId);
            // 建单前若有人探测过这个 ID 并留下「不存在」占位，这里清掉
            evict(orderId);
        });
    }

    @Override
    public boolean updateVersion(Order order) {
        boolean updated = delegate.updateVersion(order);
        // 只有 CAS 真的写成功才失效。乐观锁冲突时库里没变，缓存里的值仍然是对的，
        // 白删一次只是让下一个请求多打一次库
        if (updated && order != null && order.getId() != null) {
            String orderId = order.getId().getValue();
            afterCommitExecutor.afterCommit(() -> evict(orderId));
        }
        return updated;
    }

    @Override
    public Order findById(OrderId id) {
        return delegate.findById(id);
    }

    @Override
    public List<OrderId> findSubmittedOrderIdsOlderThan(long submittedBeforeEpochMs) {
        return delegate.findSubmittedOrderIdsOlderThan(submittedBeforeEpochMs);
    }

    /**
     * 失效缓存。异常只记录不外抛：数据已经提交了，不能因为删缓存失败把成功的写搞成失败。
     * 代价是短暂读到旧值，TTL 兜底。
     */
    private void evict(String orderId) {
        try {
            cache.evict(orderId);
            stats.recordEviction();
        } catch (RuntimeException ex) {
            stats.recordCacheError();
            log.warn("Cache evict failed for orderId={}, stale value may be served until TTL: {}",
                    orderId, ex.getMessage());
        }
    }

    /**
     * 登记进存在性过滤器。这是<b>唯一一处失败会影响正确性</b>的地方：ID 没进过滤器，
     * 之后对它的查询都会被防穿透短路成「不存在」，直到过滤器整体重建。所以打 ERROR 并带上
     * orderId，好让人捞出来补登记。也是过滤器默认不开的原因之一。
     */
    private void register(String orderId) {
        if (idFilter == null) {
            return;
        }
        try {
            idFilter.add(orderId);
        } catch (RuntimeException ex) {
            stats.recordCacheError();
            log.error("OrderIdFilter add FAILED for orderId={} — this order will be treated as non-existent "
                    + "by the penetration guard until the filter is rebuilt. Re-register it manually.",
                    orderId, ex);
        }
    }
}
