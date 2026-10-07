package com.ofs.app.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 预建 Kafka Topics：五个状态流转各一个，各 3 个分区。
 * {@link KafkaOutboundHandler} 用 orderId 当 key，同一订单的消息因此落同一分区
 * （Kafka 只保证<b>分区内</b>有序，topic 整体无序——取舍与现状见该类注释）。
 *
 * <p>只有 order.created 建了 -dlt：DLT 由消费者侧的 {@code @RetryableTopic} 决定，
 * 目前只有 order.created 挂了消费者。新增消费者时记得一并补对应的 DLT。
 *
 * <p>仅当 ofs.scenario.kafka.enabled=true 时激活，避免未启动 Kafka 时触发 Admin 连接。
 */
@Configuration
@ConditionalOnProperty(name = "ofs.scenario.kafka.enabled", havingValue = "true")
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic orderCreatedTopic() {
        return topic("order.created");
    }

    @Bean
    public NewTopic orderCreatedDltTopic() {
        return topic("order.created-dlt");
    }

    @Bean
    public NewTopic orderSubmittedTopic() {
        return topic("order.submitted");
    }

    @Bean
    public NewTopic orderPaidTopic() {
        return topic("order.paid");
    }

    @Bean
    public NewTopic orderPaidDltTopic() {
        return topic("order.paid-dlt");
    }

    @Bean
    public NewTopic orderShippedTopic() {
        return topic("order.shipped");
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return topic("order.cancelled");
    }
}
