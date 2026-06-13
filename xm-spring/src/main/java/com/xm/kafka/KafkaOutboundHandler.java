package com.xm.kafka;

import com.xm.scenario.transaction.localmessage.LocalMessageTxSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Outbox Relay 出站处理器：将 PendingMessage 同步发布到 Kafka。
 *
 * 使用同步 get() 而非异步回调，确保发送失败时抛异常 → Relay 不标记 markSent → 下次扫描重试。
 * Key = messageId（String），Kafka 按 key hash 路由分区，保证同一消息不跨分区乱序。
 */
@Component
@ConditionalOnProperty(name = "xm.scenario.kafka.enabled", havingValue = "true")
public class KafkaOutboundHandler implements Consumer<LocalMessageTxSupport.PendingMessage> {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboundHandler.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOutboundHandler(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void accept(LocalMessageTxSupport.PendingMessage message) {
        String key = String.valueOf(message.id());
        try {
            SendResult<String, String> result = kafkaTemplate
                    .send(message.topic(), key, message.payload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("[kafka-producer] sent topic={} partition={} offset={} messageId={}",
                    message.topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    message.id());
        } catch (Exception e) {
            throw new RuntimeException("Kafka send failed for messageId=" + message.id(), e);
        }
    }
}
