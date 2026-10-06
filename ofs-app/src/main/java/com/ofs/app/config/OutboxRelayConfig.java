package com.ofs.app.config;

import com.ofs.app.kafka.KafkaOutboundHandler;
import com.ofs.domain.shared.consumer.IdempotentMessageProcessor;
import com.ofs.domain.shared.idempotency.InMemoryIdempotencyKeyStore;
import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport;
import com.ofs.domain.transaction.localmessage.OutboxRelayRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.function.Consumer;

/**
 * Outbox 闭环：定时扫表 → outboundHandler → markSent。
 *
 * outboundHandler 优先级：
 *   1. KafkaOutboundHandler（ofs.scenario.kafka.enabled=true 时注入）→ 发 Kafka
 *   2. 降级：幂等日志处理器（本地模拟下游消费）
 *
 * 需 {@code ofs.scenario.transaction=memory|jdbc} 且 {@code ofs.scenario.outbox-relay.enabled=true}。
 *
 * <p><b>为什么用 @ConditionalOnExpression 盯着 transaction 属性，而不是 @ConditionalOnBean(LocalMessageTxSupport)：</b>
 * {@code @ConditionalOnBean} 只保证在<b>自动配置类</b>上可靠——它在配置类被处理的那一刻求值，
 * 而用户自己的 {@code @Configuration} 之间没有顺序保证。这里踩过一次实打实的坑：
 * {@code LocalMessageTxSupport} 由 {@link OrderScenarioConfig} 定义，跑测试（classes 目录）时
 * 恰好先注册、条件通过；<b>打成 jar 之后扫描顺序变了，条件求值时 bean 还没注册，整个 Relay 静默不装配</b>——
 * 测试全绿，容器里却一条消息都发不出去。
 * 换成属性条件后不再依赖任何顺序：下面的表达式与 OrderScenarioConfig 里两个
 * LocalMessageTxSupport bean 的 {@code @ConditionalOnProperty} 完全对齐。
 * 回归保护见 {@code OrderOutboxIntegrationTest.relayIsWiredAndDrainsPendingMessages}。
 */
@Configuration
@EnableScheduling
@ConditionalOnExpression(
        "'${ofs.scenario.transaction:}' == 'memory' or '${ofs.scenario.transaction:}' == 'jdbc'")
@ConditionalOnProperty(name = "ofs.scenario.outbox-relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayConfig {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayConfig.class);

    @Bean
    public OutboxRelayRunner outboxRelayRunner(
            LocalMessageTxSupport localMessageTxSupport,
            @Autowired(required = false) @Nullable KafkaOutboundHandler kafkaOutboundHandler) {
        Consumer<LocalMessageTxSupport.PendingMessage> handler;
        if (kafkaOutboundHandler != null) {
            log.info("[outbox-relay] using Kafka outbound handler");
            handler = kafkaOutboundHandler;
        } else {
            log.info("[outbox-relay] Kafka disabled — using in-process log handler");
            IdempotentMessageProcessor processor = new IdempotentMessageProcessor(new InMemoryIdempotencyKeyStore());
            handler = msg -> {
                // 幂等键用 orderId:eventType，而不是 messageId——这才是业务上的事件唯一标识，
                // 和真实下游（Kafka 消费者）的去重口径保持一致
                String key = msg.aggregateId() + ":" + msg.eventType();
                var result = processor.process(key, msg.payload(), payload -> {
                    log.info("[outbox→consumer] topic={} key={} id={} payload={}",
                            msg.topic(), key, msg.id(), payload);
                    return "ok";
                });
                if (!result.processed()) {
                    log.debug("[outbox] duplicate skip messageId={}", msg.id());
                }
            };
        }
        return new OutboxRelayRunner(localMessageTxSupport, handler);
    }

    @Bean
    public OutboxRelayScheduledJob outboxRelayScheduledJob(OutboxRelayRunner runner) {
        return new OutboxRelayScheduledJob(runner);
    }

    static class OutboxRelayScheduledJob {
        private final OutboxRelayRunner runner;

        OutboxRelayScheduledJob(OutboxRelayRunner runner) {
            this.runner = runner;
        }

        @Scheduled(fixedDelayString = "${ofs.scenario.outbox-relay.interval-ms:5000}")
        public void tick() {
            int n = runner.relayBatch(50);
            if (n > 0) {
                log.debug("Outbox relay batch sent count={}", n);
            }
        }
    }
}
