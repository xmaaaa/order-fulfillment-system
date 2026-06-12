package com.xm.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 预建 Kafka Topics。分 3 个分区：同一订单消息按 orderId 落同一分区，保证顺序。
 * 仅当 xm.scenario.kafka.enabled=true 时激活，避免未启动 Kafka 时触发 Admin 连接。
 */
@Configuration
@ConditionalOnProperty(name = "xm.scenario.kafka.enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name("order.created")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCreatedDltTopic() {
        return TopicBuilder.name("order.created-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
