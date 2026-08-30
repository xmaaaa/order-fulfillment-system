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
import com.ofs.domain.order.domain.model.OrderRepository;
import com.ofs.domain.order.domain.service.OrderDomainService;
import com.ofs.domain.order.infrastructure.repository.InMemoryOrderRepository;
import com.ofs.domain.shared.idempotency.IdempotencyKeyStore;
import com.ofs.domain.shared.idempotency.InMemoryIdempotencyKeyStore;
import com.ofs.domain.transaction.localmessage.InMemoryLocalMessageTxSupport;
import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Bean
    public OrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    @Bean
    public OrderDomainService orderDomainService(OrderRepository orderRepository) {
        return new OrderDomainService(orderRepository);
    }

    @Bean
    public OrderCommandService orderCommandService(OrderDomainService orderDomainService,
                                                   @Autowired(required = false) LockPolicy lockPolicy,
                                                   IdempotencyKeyStore idempotencyKeyStore,
                                                   @Autowired(required = false) LocalMessageTxSupport localMessageTxSupport) {
        OrderCommandService base = new OrderCommandServiceImpl(orderDomainService);
        if (localMessageTxSupport != null) {
            base = new LocalMessageOrderCommandService(base, localMessageTxSupport);
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
