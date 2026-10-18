package com.ofs.app;

import com.ofs.app.web.OfsAppApplication;
import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport;
import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport.PendingMessage;
import com.ofs.domain.transaction.localmessage.OutboxRelayRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Outbox 端到端（不需要 Kafka，用进程内 relay handler）：
 * 每一次状态流转都应在本地消息表留下一条待发消息，且能被 relay 扫空。
 *
 * <p>relay 保持启用（要验证它确实装配上了），但把扫描间隔设成 1 小时——@Scheduled 的 fixedDelay
 * 只在启动时跑一次，之后要等 1 小时，所以测试期间不会有后台线程偷偷把消息扫走，
 * 断言是确定性的；relay 本身用手动调 {@link OutboxRelayRunner#relayBatch} 验证。
 */
@SpringBootTest(classes = OfsAppApplication.class, properties = {
        "management.otlp.tracing.endpoint=",
        "ofs.scenario.transaction=memory",
        "ofs.scenario.outbox-relay.enabled=true",
        "ofs.scenario.outbox-relay.interval-ms=3600000"
})
@AutoConfigureMockMvc
class OrderOutboxIntegrationTest {

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
    private LocalMessageTxSupport outbox;

    private List<PendingMessage> pendingFor(String orderId) {
        return outbox.scanPending(500).stream()
                .filter(m -> orderId.equals(m.aggregateId()))
                .toList();
    }

    @Test
    void everyTransitionLeavesAnOutboxMessage() throws Exception {
        String orderId = createDraft();

        mockMvc.perform(post("/order/{id}/submit", orderId)).andExpect(status().isOk());
        mockMvc.perform(post("/order/{id}/paid", orderId).param("paymentId", "PAY-001"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/order/{id}/ship", orderId)).andExpect(status().isOk());

        Map<String, PendingMessage> byType = pendingFor(orderId).stream()
                .collect(Collectors.toMap(PendingMessage::eventType, Function.identity()));

        assertThat(byType).as("四次流转应各留一条 outbox 消息")
                .containsOnlyKeys("OrderCreated", "OrderSubmitted", "OrderPaid", "OrderShipped");
        assertThat(byType.get("OrderCreated").topic()).isEqualTo("order.created");
        assertThat(byType.get("OrderSubmitted").topic()).isEqualTo("order.submitted");
        assertThat(byType.get("OrderPaid").topic()).isEqualTo("order.paid");
        assertThat(byType.get("OrderShipped").topic()).isEqualTo("order.shipped");

        // 分区键必须是 orderId，否则同一订单的消息会散到不同分区
        assertThat(byType.values()).allSatisfy(m ->
                assertThat(m.aggregateId()).isEqualTo(orderId));

        // 支付单号要能传给下游
        assertThat(byType.get("OrderPaid").payload()).contains("\"paymentId\":\"PAY-001\"");
    }

    @Test
    void cancelLeavesAnOutboxMessage() throws Exception {
        String orderId = createDraft();
        mockMvc.perform(post("/order/{id}/cancel", orderId)).andExpect(status().isOk());

        assertThat(pendingFor(orderId))
                .extracting(PendingMessage::eventType)
                .contains("OrderCancelled");
    }

    /**
     * relay 必须真的装配起来了。{@code OutboxRelayConfig} 用的是
     * {@code @ConditionalOnBean(LocalMessageTxSupport.class)}——这个条件在普通 @Configuration 上
     * 依赖类处理顺序，是出了名的易碎，所以这里直接断言 bean 在，别等到线上才发现事件压根没发出去。
     */
    @Test
    void relayIsWiredAndDrainsPendingMessages(@Autowired OutboxRelayRunner relay) throws Exception {
        String orderId = createDraft();
        mockMvc.perform(post("/order/{id}/submit", orderId)).andExpect(status().isOk());

        assertThat(pendingFor(orderId)).hasSize(2);

        int sent = relay.relayBatch(500);
        assertThat(sent).as("relay 应把待发消息投出去").isGreaterThanOrEqualTo(2);
        assertThat(pendingFor(orderId)).as("投递成功后不应再是 pending").isEmpty();
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
