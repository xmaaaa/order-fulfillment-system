package com.xm.kafka;

import com.xm.web.XmBootStarter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端 Kafka 集成测试（Testcontainers）：
 *
 *  HTTP 创建草稿订单
 *    → LocalMessageTxSupport 写 outbox
 *    → OutboxRelayRunner 定时扫表（每 500ms）
 *    → KafkaOutboundHandler 发布到 order.created topic
 *    → TestVerifierListener 接收并断言
 *
 * 使用真实 Kafka 容器，验证 at-least-once 投递语义。
 */
@SpringBootTest(classes = {XmBootStarter.class, OrderKafkaIntegrationTest.TestVerifierConfig.class},
        properties = {
                "management.otlp.tracing.endpoint=",
                "xm.scenario.kafka.enabled=true",
                "xm.scenario.outbox-relay.enabled=true",
                "xm.scenario.outbox-relay.interval-ms=500"
        })
@AutoConfigureMockMvc
@Testcontainers
class OrderKafkaIntegrationTest {

    static {
        System.setProperty("csp.sentinel.log.dir", "target/sentinel");
    }

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideKafkaBootstrapServers(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    private static final BlockingQueue<ConsumerRecord<String, String>> received = new LinkedBlockingQueue<>();

    @BeforeEach
    void clearQueue() {
        received.clear();
    }

    private static final String DRAFT_REQUEST = """
            {
              "userId": "user-kafka-test",
              "lines": [
                {"skuId": "SKU-001", "quantity": 1, "price": 99.00}
              ]
            }
            """;

    @Test
    void draftOrderCreationPublishesEventToKafka() throws Exception {
        String response = mockMvc.perform(post("/order/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = response.replaceAll("^.*\"orderId\":\"([^\"]+)\".*$", "$1");

        // 最多等 15 秒：outbox relay 每 500ms 扫一次，Kafka 网络延迟在容器内极低
        ConsumerRecord<String, String> record = received.poll(15, TimeUnit.SECONDS);

        assertThat(record).as("Expected Kafka message on order.created but timed out").isNotNull();
        assertThat(record.topic()).isEqualTo("order.created");
        assertThat(record.value()).contains(orderId);
    }

    @Test
    void multipleOrdersEachProduceAnEvent() throws Exception {
        int count = 3;
        for (int i = 0; i < count; i++) {
            mockMvc.perform(post("/order/draft")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(DRAFT_REQUEST))
                    .andExpect(status().isOk());
        }

        int receivedCount = 0;
        for (int i = 0; i < count; i++) {
            ConsumerRecord<String, String> record = received.poll(15, TimeUnit.SECONDS);
            assertThat(record).as("Expected %d Kafka messages, only got %d", count, receivedCount).isNotNull();
            receivedCount++;
        }
        assertThat(receivedCount).isEqualTo(count);
    }

    /**
     * 测试专用 Consumer Group，独立于 fulfillment-group / notification-group，
     * 仅用于断言消息已到达 Kafka。
     */
    @TestConfiguration
    static class TestVerifierConfig {

        @KafkaListener(
                topics = "order.created",
                groupId = "test-verifier",
                id = "test-verifier-listener"
        )
        public void capture(ConsumerRecord<String, String> record) {
            received.offer(record);
        }
    }
}
