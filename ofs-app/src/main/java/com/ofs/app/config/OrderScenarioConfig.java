package com.ofs.app.config;

import com.ofs.app.cache.RedissonLockStrategy;
import com.ofs.domain.concurrent.lock.*;
import com.ofs.domain.order.application.command.IdempotentOrderCommandService;
import com.ofs.domain.concurrent.lock.delegate.DelegatingInventoryLockStrategy;
import com.ofs.domain.concurrent.lock.delegate.DelegatingOrderLockStrategy;
import com.ofs.domain.concurrent.lock.delegate.DelegatingUserLockStrategy;
import com.ofs.domain.concurrent.lock.delegate.InMemoryLockStrategy;
import com.ofs.domain.order.application.command.LocalMessageOrderCommandService;
import com.ofs.domain.order.application.command.LockedOrderCommandService;
import com.ofs.domain.order.application.command.OrderCommandService;
import com.ofs.domain.order.application.command.OrderCommandServiceImpl;
import com.ofs.domain.order.application.query.CachedOrderQueryService;
import com.ofs.domain.order.application.query.OrderCacheSettings;
import com.ofs.domain.order.application.query.OrderCacheStats;
import com.ofs.domain.order.application.query.OrderIdFilter;
import com.ofs.domain.order.application.query.OrderQueryService;
import com.ofs.domain.order.application.query.OrderQueryServiceImpl;
import com.ofs.domain.order.application.query.OrderViewCache;
import com.ofs.domain.order.domain.model.OrderRepository;
import com.ofs.domain.order.domain.service.OrderDomainService;
import com.ofs.domain.order.infrastructure.repository.CacheEvictingOrderRepository;
import com.ofs.domain.order.infrastructure.repository.InMemoryOrderRepository;
import com.ofs.domain.shared.idempotency.IdempotencyKeyStore;
import com.ofs.domain.shared.idempotency.InMemoryIdempotencyKeyStore;
import com.ofs.domain.shared.transaction.AfterCommitExecutor;
import com.ofs.domain.transaction.localmessage.InMemoryLocalMessageTxSupport;
import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 订单场景装配：OrderRepository、OrderCommandService、LockPolicy、LocalMessageTxSupport。
 * 锁：memory=学习用 / redisson=框架 / none=不加锁。
 * 事务(本地消息表)：memory=学习用 / jdbc=生产(需 outbox 表) / 不配置=不用。
 */
@Configuration
public class OrderScenarioConfig {

    /**
     * 订单仓储。开缓存时套一层失效装饰器——<b>所有</b>写路径都收敛到 save/updateVersion，
     * 挂这里才不会漏掉 TCC / Saga / 超时调度器那几条绕过 OrderCommandService 的路径；
     * 而失效动作本身通过 {@link AfterCommitExecutor} 推迟到事务提交之后，不在事务内发布副作用。
     * 详见 {@link CacheEvictingOrderRepository} 的类注释。
     */
    @Bean
    public OrderRepository orderRepository(@Autowired(required = false) OrderViewCache orderViewCache,
                                           @Autowired(required = false) OrderCacheStats orderCacheStats,
                                           @Autowired(required = false) OrderIdFilter orderIdFilter,
                                           @Autowired(required = false) AfterCommitExecutor afterCommitExecutor) {
        OrderRepository base = new InMemoryOrderRepository();
        if (orderViewCache == null) {
            return base;
        }
        return new CacheEvictingOrderRepository(
                base, orderViewCache, orderCacheStats, orderIdFilter, afterCommitExecutor);
    }

    @Bean
    public OrderDomainService orderDomainService(OrderRepository orderRepository) {
        return new OrderDomainService(orderRepository);
    }

    /**
     * 写侧装饰器链，由内到外：Impl → 本地消息表 → 订单锁 → 幂等。
     *
     * <p>这里<b>没有</b>缓存失效层。失效统一由 {@link CacheEvictingOrderRepository} 负责
     * （见 orderRepository bean）：所有写路径都收敛到仓储，挂那里才不会漏掉 TCC/Saga/超时调度器；
     * 而它通过 {@code AfterCommitExecutor} 把失效推迟到事务提交后，时序也是对的。
     */
    @Bean
    public OrderCommandService orderCommandService(OrderDomainService orderDomainService,
                                                   OrderRepository orderRepository,
                                                   @Autowired(required = false) LockPolicy lockPolicy,
                                                   IdempotencyKeyStore idempotencyKeyStore,
                                                   @Autowired(required = false) LocalMessageTxSupport localMessageTxSupport) {
        OrderCommandService base = new OrderCommandServiceImpl(orderDomainService);
        if (localMessageTxSupport != null) {
            // 仓储传进去只为在事务内把刚写完的订单读回来，取 userId/version 放进事件
            base = new LocalMessageOrderCommandService(base, localMessageTxSupport, orderRepository);
        }
        if (lockPolicy != null) {
            base = new LockedOrderCommandService(base, lockPolicy);
        }
        return new IdempotentOrderCommandService(base, idempotencyKeyStore, 10 * 60 * 1000L);
    }

    @Bean
    public IdempotencyKeyStore idempotencyKeyStore() {
        return new InMemoryIdempotencyKeyStore();
    }

    /**
     * CQRS 读侧。此前 OrderQueryService 从未装配，GET 接口直接走写侧 OrderCommandService.getOrder()，
     * 读写路径混在一起；这里补上装配，读路径独立出来，缓存装饰器才有地方挂。
     *
     * <p>{@code ofs.scenario.cache=none}（默认）时 OrderViewCache 不存在，这里返回裸的
     * OrderQueryServiceImpl，行为与加缓存之前完全一致。缓存实现的装配见 {@link OrderCacheConfig}。
     */
    @Bean
    public OrderQueryService orderQueryService(OrderRepository orderRepository,
                                               @Autowired(required = false) OrderViewCache orderViewCache,
                                               @Autowired(required = false) OrderCacheSettings orderCacheSettings,
                                               @Autowired(required = false) OrderCacheStats orderCacheStats,
                                               @Autowired(required = false) OrderIdFilter orderIdFilter,
                                               @Autowired(required = false)
                                               @Qualifier("orderCacheRebuildLock") LockStrategy rebuildLock) {
        OrderQueryService base = new OrderQueryServiceImpl(orderRepository);
        if (orderViewCache == null) {
            return base;
        }
        return new CachedOrderQueryService(
                base, orderViewCache, orderCacheSettings, orderCacheStats, orderIdFilter, rebuildLock);
    }

    // ---------- 锁：学习用（订单/库存/用户 不同 lease：30s/10s/5s） ----------
    @Bean
    @ConditionalOnProperty(name = "ofs.scenario.lock", havingValue = "memory")
    public LockPolicy memoryLockPolicy() {
        LockStrategy base = new InMemoryLockStrategy("MEMORY");
        return new CompositeLockPolicy(
                new DelegatingOrderLockStrategy(base),
                new DelegatingInventoryLockStrategy(base),
                new DelegatingUserLockStrategy(base)
        );
    }

    // ---------- 锁：框架用（Redisson raw，Delegating 负责各维 key 与 lease） ----------
    @Bean
    @ConditionalOnProperty(name = "ofs.scenario.lock", havingValue = "redisson")
    @ConditionalOnBean(RedissonClient.class)
    public LockPolicy redissonLockPolicy(RedissonClient redissonClient) {
        LockStrategy base = RedissonLockStrategy.raw(redissonClient);
        return new CompositeLockPolicy(
                new DelegatingOrderLockStrategy(base),
                new DelegatingInventoryLockStrategy(base),
                new DelegatingUserLockStrategy(base)
        );
    }

    // ---------- 本地消息表：学习用（内存，无 DB） ----------
    @Bean
    @ConditionalOnProperty(name = "ofs.scenario.transaction", havingValue = "memory")
    public LocalMessageTxSupport memoryLocalMessageTxSupport() {
        return new InMemoryLocalMessageTxSupport();
    }

    // ---------- 本地消息表：生产用（JdbcLocalMessageTxSupport，需 outbox_message 表） ----------
    @Bean
    @ConditionalOnProperty(name = "ofs.scenario.transaction", havingValue = "jdbc")
    @ConditionalOnBean(DataSource.class)
    public LocalMessageTxSupport jdbcLocalMessageTxSupport(DataSource dataSource,
                                                          PlatformTransactionManager transactionManager) {
        return new com.ofs.domain.transaction.localmessage.JdbcLocalMessageTxSupport(
                dataSource, new TransactionTemplate(transactionManager));
    }
}
