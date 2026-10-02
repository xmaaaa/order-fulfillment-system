package com.ofs.app.web;

import com.ofs.domain.order.application.query.CachedOrderQueryService;
import com.ofs.domain.order.application.query.OrderCacheStats;
import com.ofs.domain.order.application.query.OrderQueryService;
import com.ofs.domain.order.application.query.OrderViewCache;
import com.ofs.domain.order.domain.model.OrderRepository;
import com.ofs.domain.order.infrastructure.repository.CacheEvictingOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 开启缓存（{@code ofs.scenario.cache=memory}）后的端到端验证。用 memory 而不是 redis，
 * 这样 CI 里不需要起 Redis 也能跑；Redisson 实现本身的序列化行为另有单测覆盖。
 *
 * <p>这个测试真正要盯住的是<b>读写一致性</b>：每个写操作之后都 GET 一次，
 * 缓存失效若漏了任何一条写路径，这里会立刻读到旧状态。尤其是 TCC / Saga 那两条
 * 绕过 OrderCommandService、直接调 OrderDomainService 的路径。
 */
@SpringBootTest(classes = OfsAppApplication.class, properties = {
        "management.otlp.tracing.endpoint=",
        "ofs.scenario.outbox-relay.enabled=false",
        "ofs.scenario.cache=memory"
})
@AutoConfigureMockMvc
class OrderControllerCachedIntegrationTest {

    static {
        System.setProperty("csp.sentinel.log.dir", "target/sentinel");
    }

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
    private OrderQueryService orderQueryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderCacheStats orderCacheStats;

    @Autowired
    private OrderViewCache orderViewCache;

    @Test
    void cacheDecoratorsAreActuallyWired() {
        assertNotNull(orderViewCache);
        assertInstanceOf(CachedOrderQueryService.class, orderQueryService,
                "cache=memory 时读侧应被 CachedOrderQueryService 包住");
        assertInstanceOf(CacheEvictingOrderRepository.class, orderRepository,
                "cache=memory 时仓储应被 CacheEvictingOrderRepository 包住");
    }

    @Test
    void repeatedReadsHitCache() throws Exception {
        String orderId = createDraft();
        long hitsBefore = orderCacheStats.hits();

        mockMvc.perform(get("/order/{orderId}", orderId)).andExpect(status().isOk());
        mockMvc.perform(get("/order/{orderId}", orderId)).andExpect(status().isOk());
        mockMvc.perform(get("/order/{orderId}", orderId)).andExpect(status().isOk());

        assertTrue(orderCacheStats.hits() > hitsBefore,
                "重复读同一订单应产生缓存命中，实际 " + orderCacheStats);
    }

    @Test
    void lifecycleStaysConsistentWithCacheEnabled() throws Exception {
        String orderId = createDraft();

        // 每一步都「先读（灌缓存）→ 再写 → 再读（必须看到新状态）」
        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(jsonPath("$.state", is("DRAFT")));

        mockMvc.perform(post("/order/{orderId}/submit", orderId)).andExpect(status().isOk());
        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("SUBMITTED")))
                .andExpect(jsonPath("$.totalAmount", is(198.0)));

        mockMvc.perform(post("/order/{orderId}/paid", orderId).param("paymentId", "PAY-001"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(jsonPath("$.state", is("PAID")));

        mockMvc.perform(post("/order/{orderId}/ship", orderId)).andExpect(status().isOk());
        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(jsonPath("$.state", is("SHIPPED")));
    }

    /**
     * TCC 走 OrderSubmitWithPaymentTccService → OrderDomainService，绕过 OrderCommandService。
     * 只在应用服务层挂失效的话，这里读到的会是 SUBMITTED。
     */
    @Test
    void tccPaymentInvalidatesCache() throws Exception {
        String orderId = createDraft();
        mockMvc.perform(post("/order/{orderId}/submit", orderId)).andExpect(status().isOk());

        // 关键：先读一次，把 SUBMITTED 灌进缓存
        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(jsonPath("$.state", is("SUBMITTED")));

        mockMvc.perform(post("/order/{orderId}/submit-with-payment-tcc", orderId)
                        .param("amount", "198")
                        .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("PAID")));
    }

    /** Saga 同理：orderDomainService.markPaid 绕过应用服务层 */
    @Test
    void sagaPaymentInvalidatesCache() throws Exception {
        String orderId = createDraft();
        mockMvc.perform(post("/order/{orderId}/submit", orderId)).andExpect(status().isOk());

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(jsonPath("$.state", is("SUBMITTED")));

        mockMvc.perform(post("/order/{orderId}/submit-with-payment-saga", orderId)
                        .param("amount", "198")
                        .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("PAID")));
    }

    /** 空值缓存不能把 404 变成别的东西，重复查也还得是 404 */
    @Test
    void missingOrderStaysNotFoundOnRepeatedReads() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/order/{orderId}", "ORD-missing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")));
        }
        assertTrue(orderCacheStats.absentHits() > 0,
                "第二、三次应命中「不存在」占位，实际 " + orderCacheStats);
    }

    /** 失败的写不该让缓存里的正确旧值消失，也不该让状态变化 */
    @Test
    void invalidTransitionKeepsStateConsistent() throws Exception {
        String orderId = createDraft();
        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(jsonPath("$.state", is("DRAFT")));

        mockMvc.perform(post("/order/{orderId}/ship", orderId))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("DRAFT")));
    }

    private String createDraft() throws Exception {
        return mockMvc.perform(post("/order/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("^.*\\\"orderId\\\":\\\"([^\\\"]+)\\\".*$", "$1");
    }
}
