package com.ofs.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofs.app.cache.RedissonLockStrategy;
import com.ofs.app.cache.RedissonOrderIdFilter;
import com.ofs.app.cache.RedissonOrderViewCache;
import com.ofs.app.transaction.SpringAfterCommitExecutor;
import com.ofs.domain.concurrent.lock.LockStrategy;
import com.ofs.domain.concurrent.lock.delegate.InMemoryLockStrategy;
import com.ofs.domain.order.application.query.InMemoryOrderViewCache;
import com.ofs.domain.order.application.query.OrderCacheSettings;
import com.ofs.domain.order.application.query.OrderCacheStats;
import com.ofs.domain.order.application.query.OrderIdFilter;
import com.ofs.domain.order.application.query.OrderViewCache;
import com.ofs.domain.shared.transaction.AfterCommitExecutor;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单缓存（Cache-Aside）基础设施装配。<b>只提供实现</b>；把它们套到读写两侧的
 * 装饰器链在 {@link OrderScenarioConfig}——和锁那套的分工一致（策略在 ScenarioConfig，
 * Redisson 实现在 cache 包）。
 *
 * <p>开关 {@code ofs.scenario.cache}：
 * <ul>
 *   <li>{@code none}（默认）——本配置整体不装载，读写侧退化成无缓存，行为与加缓存前完全一致</li>
 *   <li>{@code memory}——JVM 内缓存 + JVM 内重建锁，单机/单测用，不需要 Redis</li>
 *   <li>{@code redis}——Redisson 实现，多机共享；需要 {@code redis.enabled=true}</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(OrderCacheProperties.class)
@ConditionalOnExpression("'${ofs.scenario.cache:none}' != 'none'")
public class OrderCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(OrderCacheConfig.class);

    @Bean
    public OrderCacheStats orderCacheStats() {
        return new OrderCacheStats();
    }

    /**
     * 启动自检。挡的是一个静默陷阱：配了 {@code ofs.scenario.cache=redis} 却忘了
     * {@code redis.enabled=true}，于是没有 RedissonClient → 没有 OrderViewCache →
     * 装饰器不生效 → 缓存悄无声息地完全没开，压测半天以为是缓存不管用。
     */
    @Bean
    public ApplicationRunner orderCacheStartupCheck(@Value("${ofs.scenario.cache:none}") String mode,
                                                    ObjectProvider<OrderViewCache> cacheProvider,
                                                    ObjectProvider<OrderIdFilter> filterProvider) {
        return args -> {
            OrderViewCache cache = cacheProvider.getIfAvailable();
            if (cache == null) {
                log.warn("ofs.scenario.cache={} 但没有 OrderViewCache bean，订单缓存实际未启用。"
                        + "cache=redis 时请确认 redis.enabled=true 且 RedissonClient 装配成功。", mode);
                return;
            }
            log.info("订单缓存已启用: mode={}, impl={}, 防穿透过滤器={}",
                    mode, cache.getClass().getSimpleName(),
                    filterProvider.getIfAvailable() != null ? "on" : "off");
        };
    }

    /**
     * 缓存失效的执行时机：有活跃事务就挂到提交后，没有就立即执行。
     * 这是 {@code CacheEvictingOrderRepository} 能挂在仓储层（覆盖全部写路径）
     * 却又不在事务内发布副作用的关键。
     */
    @Bean
    public AfterCommitExecutor afterCommitExecutor() {
        return new SpringAfterCommitExecutor();
    }

    @Bean
    public OrderCacheSettings orderCacheSettings(OrderCacheProperties properties) {
        return new OrderCacheSettings(
                properties.getTtl().toMillis(),
                properties.getAbsentTtl().toMillis(),
                properties.getJitter().toMillis(),
                properties.getLockWait().toMillis(),
                properties.getLockLease().toMillis());
    }

    // ---------- 缓存实现：memory=学习/单机 ----------

    @Bean
    @ConditionalOnProperty(name = "ofs.scenario.cache", havingValue = "memory")
    public OrderViewCache inMemoryOrderViewCache() {
        return new InMemoryOrderViewCache();
    }

    // ---------- 缓存实现：redis=多机共享 ----------

    @Bean
    @ConditionalOnProperty(name = "ofs.scenario.cache", havingValue = "redis")
    @ConditionalOnBean(RedissonClient.class)
    public OrderViewCache redissonOrderViewCache(RedissonClient redissonClient,
                                                 ObjectMapper objectMapper,
                                                 OrderCacheProperties properties) {
        return new RedissonOrderViewCache(redissonClient, objectMapper, properties.getKeyPrefix());
    }

    /**
     * 防穿透过滤器。默认关闭——启用前必须全量预热历史订单 ID，
     * 否则老订单会被误判成不存在，原因见 {@link OrderIdFilter}。
     */
    @Bean
    @ConditionalOnProperty(name = "ofs.scenario.order-cache.bloom-filter.enabled", havingValue = "true")
    @ConditionalOnBean(RedissonClient.class)
    public OrderIdFilter redissonOrderIdFilter(RedissonClient redissonClient, OrderCacheProperties properties) {
        OrderCacheProperties.BloomFilter bloom = properties.getBloomFilter();
        return new RedissonOrderIdFilter(
                redissonClient,
                bloom.getKey(),
                bloom.getExpectedInsertions(),
                bloom.getFalseProbability());
    }

    // ---------- 防击穿：重建锁 ----------

    /**
     * 缓存重建锁。key 前缀由 {@code CachedOrderQueryService} 加（{@code cache:order:}），
     * 与业务订单锁的 key 空间隔离——不然读侧重建缓存会挡住同订单的写。
     */
    @Bean
    @ConditionalOnProperty(name = "ofs.scenario.order-cache.rebuild-lock", havingValue = "true", matchIfMissing = true)
    public LockStrategy orderCacheRebuildLock(@org.springframework.beans.factory.annotation.Autowired(required = false)
                                              RedissonClient redissonClient) {
        // 有 Redisson 就用分布式锁（多机才真正防得住击穿）；没有就退回 JVM 锁，单机仍有效
        return redissonClient != null
                ? RedissonLockStrategy.raw(redissonClient)
                : new InMemoryLockStrategy("CACHE_REBUILD");
    }

    // ---------- 打点：绑到 Prometheus ----------

    /**
     * 把 {@link OrderCacheStats} 的计数器暴露成 Gauge。Spring Boot Actuator 会自动
     * 把 MeterBinder 类型的 bean 绑到 MeterRegistry 上，不用自己拿 registry。
     */
    @Bean
    public MeterBinder orderCacheMetrics(OrderCacheStats stats) {
        return registry -> {
            Gauge.builder("ofs.order.cache.hits", stats, OrderCacheStats::hits)
                    .description("命中且有值的次数").register(registry);
            Gauge.builder("ofs.order.cache.absent.hits", stats, OrderCacheStats::absentHits)
                    .description("命中「不存在」占位的次数（空值缓存生效）").register(registry);
            Gauge.builder("ofs.order.cache.misses", stats, OrderCacheStats::misses)
                    .description("未命中次数").register(registry);
            Gauge.builder("ofs.order.cache.loads", stats, OrderCacheStats::loads)
                    .description("回源查库次数").register(registry);
            Gauge.builder("ofs.order.cache.filtered", stats, OrderCacheStats::filtered)
                    .description("被存在性过滤器短路的次数（防穿透生效）").register(registry);
            Gauge.builder("ofs.order.cache.lock.misses", stats, OrderCacheStats::lockMisses)
                    .description("未抢到重建锁的次数（防击穿生效）").register(registry);
            Gauge.builder("ofs.order.cache.rebuild.skipped", stats, OrderCacheStats::rebuildSkipped)
                    .description("双重检查发现已回填、省掉的重建次数（防击穿真正省下的查库）").register(registry);
            Gauge.builder("ofs.order.cache.degraded.loads", stats, OrderCacheStats::degradedLoads)
                    .description("降级直查库的次数").register(registry);
            Gauge.builder("ofs.order.cache.errors", stats, OrderCacheStats::cacheErrors)
                    .description("缓存组件异常次数").register(registry);
            Gauge.builder("ofs.order.cache.evictions", stats, OrderCacheStats::evictions)
                    .description("写操作触发的失效次数").register(registry);
            Gauge.builder("ofs.order.cache.hit.rate", stats, OrderCacheStats::hitRate)
                    .description("命中率（空值命中算命中）").register(registry);
        };
    }
}
