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
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DLT 路径集成测试（Testcontainers）：
 *
 *  HTTP 创建草稿订单
 *    → Outbox Relay → Kafka order.created topic
 *    → FailingConsumer（test-fail-group）总是抛异常
 *    → @RetryableTopic 重试 2 次（共 1+1=2 次，delay=100ms）
 *    → 路由到 order.created-dlt
 *    → DltVerifierListener 捕获死信并断言头信息
 *
 * retry.attempts=2（最快路径：1次原始 + 1次重试 → DLT）
 * delay=100ms 保证测试在秒级完成。
 */
@SpringBootTest(
        classes = {XmBootStarter.class, OrderKafkaDltIntegrationTest.DltTestConfig.class},
        properties = {
                "management.otlp.tracing.endpoint=",
                "xm.scenario.kafka.enabled=true",
                "xm.scenario.outbox-relay.enabled=true",
                "xm.scenario.outbox-relay.interval-ms=500",
                "xm.scenario.kafka.retry.attempts=2",
                "xm.scenario.kafka.retry.delay-ms=100",
                "xm.scenario.kafka.retry.multiplier=1.0"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderKafkaDltIntegrationTest {

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

    private static final BlockingQueue<ConsumerRecord<String, String>> dltReceived = new LinkedBlockingQueue<>();

    @BeforeEach
    void clearQueue() {
        dltReceived.clear();
    }

    private static final String DRAFT_REQUEST = """
            {
              "userId": "user-dlt-test",
              "lines": [
                {"skuId": "SKU-DLT", "quantity": 1, "price": 50.00}
              ]
            }
            """;

    @Test
    void failingConsumerExhaustsRetriesAndPublishesToDlt() throws Exception {
        mockMvc.perform(post("/order/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_REQUEST))
                .andExpect(status().isOk());

        // 等待：outbox relay 500ms + 重试 100ms×2 + Kafka 传输 ≈ 5s 足够
        ConsumerRecord<String, String> deadLetter = dltReceived.poll(15, TimeUnit.SECONDS);

        assertThat(deadLetter).as("Expected dead letter in order.created-dlt but timed out").isNotNull();
        assertThat(deadLetter.topic()).isEqualTo("order.created-dlt");
        assertThat(deadLetter.value()).isNotBlank();
    }

    @Test
    void dltMessageCarriesExceptionHeaders() throws Exception {
        mockMvc.perform(post("/order/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_REQUEST))
                .andExpect(status().isOk());

        ConsumerRecord<String, String> deadLetter = dltReceived.poll(15, TimeUnit.SECONDS);

        assertThat(deadLetter).isNotNull();

        // 验证 DLT 消息头携带了完整的故障上下文
        var headers = deadLetter.headers();
        assertThat(headers.lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        assertThat(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)).isNotNull();
        assertThat(new String(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value()))
                .isEqualTo("order.created");
        assertThat(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)).isNotNull();
        assertThat(new String(headers.lastHeader(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP).value()))
                .isEqualTo("test-fail-group");
    }

    @TestConfiguration
    static class DltTestConfig {

        /**
         * 模拟消费失败的下游服务：始终抛出异常，触发 @RetryableTopic 重试链，最终进入 DLT。
         * 使用独立的 test-fail-group，不干扰 fulfillment/notification 正常消费路径。
         */
        @RetryableTopic(
                attempts = "${xm.scenario.kafka.retry.attempts:2}",
                backoff = @Backoff(
                        delayExpression = "${xm.scenario.kafka.retry.delay-ms:100}",
                        multiplierExpression = "${xm.scenario.kafka.retry.multiplier:1.0}"
                ),
                topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
                dltTopicSuffix = "-dlt",
                autoCreateTopics = "true"
        )
        @KafkaListener(
                topics = "order.created",
                groupId = "test-fail-group",
                id = "test-fail-listener"
        )
        public void alwaysFails(ConsumerRecord<String, String> record) {
            throw new IllegalStateException("Simulated consumer failure for DLT test | key=" + record.key());
        }

        @KafkaListener(
                topics = "order.created-dlt",
                groupId = "dlt-verifier-group",
                id = "dlt-verifier-listener"
        )
        public void captureDlt(
                ConsumerRecord<String, String> record,
                @Header(value = KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, required = false) String group) {
            // 只捕获来自 test-fail-group 的死信，过滤掉其他测试可能残留的消息
            if ("test-fail-group".equals(group)) {
                dltReceived.offer(record);
            }
        }
    }
}
