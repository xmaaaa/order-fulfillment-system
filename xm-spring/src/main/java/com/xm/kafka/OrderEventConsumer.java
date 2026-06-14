package com.xm.kafka;

import com.xm.scenario.shared.consumer.IdempotentMessageProcessor;
import com.xm.scenario.shared.idempotency.InMemoryIdempotencyKeyStore;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * 模拟两个独立下游服务通过不同 Consumer Group 订阅同一 Topic：
 *
 *  fulfillment-group  → 履约服务：按仓库路由发货
 *  notification-group → 通知服务：发送订单确认邮件/短信
 *
 * 每个 Group 独立维护消费进度，互不干扰，天然实现扇出（fan-out）。
 * 各 Group 内置幂等处理器，防止网络重试导致重复消费。
 *
 * 非阻塞重试（Non-Blocking Retry）策略：
 *   消费失败 → 发往重试 topic（order.created-retry-0/1）→ 指数退避重新消费
 *   超过最大次数 → 路由到 order.created-dlt，由 OrderDeadLetterHandler 处理
 * IdempotentMessageProcessor 在异常时释放 key，保证重试时可以重新消费。
 */
@Component
@ConditionalOnProperty(name = "xm.scenario.kafka.enabled", havingValue = "true")
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final IdempotentMessageProcessor fulfillmentProcessor =
            new IdempotentMessageProcessor(new InMemoryIdempotencyKeyStore());
    private final IdempotentMessageProcessor notificationProcessor =
            new IdempotentMessageProcessor(new InMemoryIdempotencyKeyStore());

    @RetryableTopic(
            attempts = "${xm.scenario.kafka.retry.attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${xm.scenario.kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${xm.scenario.kafka.retry.multiplier:2.0}"
            ),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "true"
    )
    @KafkaListener(
            topics = "order.created",
            groupId = "fulfillment-group",
            id = "fulfillment-listener",
            concurrency = "3"
    )
    public void onOrderCreatedFulfillment(ConsumerRecord<String, String> record) {
        var result = fulfillmentProcessor.process(record.key(), record.value(), payload -> {
            log.info("[fulfillment] order.created → routing to warehouse | partition={} offset={} payload={}",
                    record.partition(), record.offset(), payload);
            return "routed";
        });
        if (!result.processed()) {
            log.debug("[fulfillment] duplicate skipped key={}", record.key());
        }
    }

    @RetryableTopic(
            attempts = "${xm.scenario.kafka.retry.attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${xm.scenario.kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${xm.scenario.kafka.retry.multiplier:2.0}"
            ),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "true"
    )
    @KafkaListener(
            topics = "order.created",
            groupId = "notification-group",
            id = "notification-listener",
            concurrency = "3"
    )
    public void onOrderCreatedNotification(ConsumerRecord<String, String> record) {
        var result = notificationProcessor.process(record.key(), record.value(), payload -> {
            log.info("[notification] order.created → sending confirmation | partition={} offset={} payload={}",
                    record.partition(), record.offset(), payload);
            return "notified";
        });
        if (!result.processed()) {
            log.debug("[notification] duplicate skipped key={}", record.key());
        }
    }
}
