package com.ofs.app.kafka;

import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport;
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
 *
 * Key = aggregateId（即 orderId）。Kafka 的顺序保证是<b>分区内</b>的，不是 topic 内的——这些 topic
 * 各有 3 个分区。按 orderId 做 key 让同一订单的消息落同一分区，于是<b>该订单的这些消息之间</b>有序。
 *
 * <p>说实话一点：当前「一个事件类型一个 topic」，每个订单在每个 topic 上<b>只有一条消息</b>，
 * 所以 topic 内根本没有两条消息需要排序，这个保证暂时是空的。改成 orderId 是把语义摆正
 * （原先用 messageId 当 key，和 KafkaTopicConfig 注释里声称的行为对不上），
 * 并为「同一订单在同一 topic 上出现多条消息」的那天铺路——比如合并成单个 order.events，
 * 或加入退款、多次发货这类可重复事件。
 *
 * <p>真正有顺序需求的「paid 必须早于 shipped」是<b>跨 topic</b>的，Kafka 完全不保证；
 * 另外消费侧的 @RetryableTopic 会让失败消息绕道重试 topic 稍后再投，分区内顺序同样会断。
 */
@Component
@ConditionalOnProperty(name = "ofs.scenario.kafka.enabled", havingValue = "true")
public class KafkaOutboundHandler implements Consumer<LocalMessageTxSupport.PendingMessage> {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboundHandler.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOutboundHandler(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void accept(LocalMessageTxSupport.PendingMessage message) {
        String key = message.aggregateId();
        try {
            SendResult<String, String> result = kafkaTemplate
                    .send(message.topic(), key, message.payload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("[kafka-producer] sent topic={} key={} eventType={} partition={} offset={} messageId={}",
                    message.topic(),
                    key,
                    message.eventType(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    message.id());
        } catch (Exception e) {
            throw new RuntimeException("Kafka send failed for messageId=" + message.id(), e);
        }
    }
}
