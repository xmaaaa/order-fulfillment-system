package com.ofs.app.cache;

import com.ofs.app.web.OfsAppApplication;
import com.ofs.domain.order.application.query.OrderCacheStats;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 Redis 的 Cache-Aside 集成测试（Testcontainers，需要 Docker）。
 *
 * <p>{@code OrderControllerCachedIntegrationTest} 用内存实现测的是<b>逻辑</b>；
 * 这里测的是只有真 Redis 才暴露的部分：Jackson 序列化 OrderView 能不能来回、
 * TTL 有没有真的设上、「不存在」占位长什么样、布隆过滤器短路是否生效。
 */
@Testcontainers
@SpringBootTest(classes = OfsAppApplication.class, properties = {
        "management.otlp.tracing.endpoint=",
        "ofs.scenario.outbox-relay.enabled=false",
        "redis.enabled=true",
        "ofs.scenario.cache=redis",
        "ofs.scenario.order-cache.ttl=5m",
        "ofs.scenario.order-cache.absent-ttl=30s",
        "ofs.scenario.order-cache.bloom-filter.enabled=true"
})
@AutoConfigureMockMvc
class OrderRedisCacheIntegrationTest {

    static {
        System.setProperty("csp.sentinel.log.dir", "target/sentinel");
    }

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("redis.address",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    }

    private static final String KEY_PREFIX = "ofs:order:view:";

    private static final String DRAFT_REQUEST = """
            {
              "userId": "user1",
              "lines": [
                {"skuId": "SKU-001", "quantity": 2, "price": 99.00}
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderCacheStats orderCacheStats;

    private String raw(String key) {
        return redissonClient.<String>getBucket(key, StringCodec.INSTANCE).get();
    }

    private long ttlMs(String key) {
        return redissonClient.getBucket(key, StringCodec.INSTANCE).remainTimeToLive();
    }

    @Test
    void orderViewIsStoredAsReadableJsonWithTtl() throws Exception {
        String orderId = createDraft();
        String key = KEY_PREFIX + orderId;

        // 建单后还没读过，缓存应是空的（Cache-Aside 不做写时预热）
        assertThat(raw(key)).isNull();

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("DRAFT")));

        // 回填后应能直接从 Redis 读到人看得懂的 JSON
        String cached = raw(key);
        assertThat(cached).isNotNull();
        assertThat(cached)
                .contains("\"orderId\":\"" + orderId + "\"")
                .contains("\"state\":\"DRAFT\"")
                .contains("\"userId\":\"user1\"")
                .contains("SKU-001");

        // TTL 应落在 5m + 抖动上界(默认 30s) 之内，且明显大于 0
        long remaining = ttlMs(key);
        assertThat(remaining).isGreaterThan(0).isLessThanOrEqualTo(330_000L);

        // 再读一次应命中缓存，且反序列化回来的字段没丢
        long hitsBefore = orderCacheStats.hits();
        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is(orderId)))
                .andExpect(jsonPath("$.state", is("DRAFT")))
                .andExpect(jsonPath("$.totalAmount", is(198.0)))
                .andExpect(jsonPath("$.lines[0].skuId", is("SKU-001")))
                .andExpect(jsonPath("$.lines[0].quantity", is(2)));
        assertThat(orderCacheStats.hits()).isGreaterThan(hitsBefore);
    }

    @Test
    void writeEvictsRedisKey() throws Exception {
        String orderId = createDraft();
        String key = KEY_PREFIX + orderId;

        mockMvc.perform(get("/order/{orderId}", orderId)).andExpect(status().isOk());
        assertThat(raw(key)).isNotNull();

        mockMvc.perform(post("/order/{orderId}/submit", orderId)).andExpect(status().isOk());
        assertThat(raw(key)).as("写操作后 Redis 里的 key 应被删掉").isNull();

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(jsonPath("$.state", is("SUBMITTED")));
        assertThat(raw(key)).contains("\"state\":\"SUBMITTED\"");
    }

    @Test
    void tccPaymentEvictsRedisKey() throws Exception {
        String orderId = createDraft();
        mockMvc.perform(post("/order/{orderId}/submit", orderId)).andExpect(status().isOk());
        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(jsonPath("$.state", is("SUBMITTED")));

        // TCC 绕过 OrderCommandService，靠仓储层装饰器兜底
        mockMvc.perform(post("/order/{orderId}/submit-with-payment-tcc", orderId)
                        .param("amount", "198")
                        .param("userId", "user1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(jsonPath("$.state", is("PAID")));
    }

    /**
     * 布隆过滤器已开启：一个从没建过的随机 ID 应被直接短路，
     * 既不查库也不会在 Redis 留下「不存在」占位。
     */
    @Test
    void bloomFilterShortCircuitsUnknownId() throws Exception {
        String ghost = "ORD-never-existed-" + System.nanoTime();
        long filteredBefore = orderCacheStats.filtered();
        long loadsBefore = orderCacheStats.loads();

        mockMvc.perform(get("/order/{orderId}", ghost))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")));

        assertThat(orderCacheStats.filtered()).as("应被布隆过滤器短路").isGreaterThan(filteredBefore);
        assertThat(orderCacheStats.loads()).as("被短路时不该回源查库").isEqualTo(loadsBefore);
        assertThat(raw(KEY_PREFIX + ghost)).as("短路的请求不该写空值占位").isNull();
    }

    /** 建单时 ID 要进过滤器，否则新单会被防穿透误判成不存在 */
    @Test
    void newOrderPassesBloomFilter() throws Exception {
        String orderId = createDraft();
        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is(orderId)));
    }

    /** 脏数据应自愈：删掉坏条目、当未命中处理，而不是每次都反序列化失败 */
    @Test
    void corruptEntryIsDroppedAndRebuilt() throws Exception {
        String orderId = createDraft();
        String key = KEY_PREFIX + orderId;

        redissonClient.<String>getBucket(key, StringCodec.INSTANCE).set("{not valid json");

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("DRAFT")));

        assertThat(raw(key)).as("坏条目应被替换成正常 JSON").contains("\"state\":\"DRAFT\"");
    }

    private String createDraft() throws Exception {
        return mockMvc.perform(post("/order/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_REQUEST))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("^.*\\\"orderId\\\":\\\"([^\\\"]+)\\\".*$", "$1");
    }
}
