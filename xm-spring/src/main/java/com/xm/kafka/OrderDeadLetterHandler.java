package com.xm.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Dead Letter Topic 处理器：消费所有进入 order.created-dlt 的死信消息。
 *
 * 消息进入 DLT 的原因：consumer 经过 @RetryableTopic 配置的所有重试次数后仍失败。
 * DLT 消息头携带完整的故障上下文（原始 topic、分区、offset、异常信息）。
 *
 * 生产扩展方向：
 *   1. 持久化到 dead_letter 表，人工审查
 *   2. 发送告警（PagerDuty / Slack）
 *   3. 触发补偿事务（退款、库存回滚等）
 */
@Component
@ConditionalOnProperty(name = "xm.scenario.kafka.enabled", havingValue = "true")
public class OrderDeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderDeadLetterHandler.class);

    @KafkaListener(
            topics = "order.created-dlt",
            groupId = "dlt-handler-group",
            id = "dlt-handler-listener"
    )
    public void handleDeadLetter(
            ConsumerRecord<String, String> record,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionFqcn,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, required = false) String consumerGroup) {

        log.error("[DLT] Dead letter received | consumerGroup={} originalTopic={} partition={} offset={} " +
                        "exceptionType={} exceptionMessage={} key={} payload={}",
                consumerGroup, originalTopic, originalPartition, originalOffset,
                exceptionFqcn, exceptionMessage, record.key(), record.value());
    }
}
