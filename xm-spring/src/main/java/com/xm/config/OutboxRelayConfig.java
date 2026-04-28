package com.xm.config;

import com.xm.scenario.shared.consumer.IdempotentMessageProcessor;
import com.xm.scenario.shared.idempotency.InMemoryIdempotencyKeyStore;
import com.xm.scenario.transaction.localmessage.LocalMessageTxSupport;
import com.xm.scenario.transaction.localmessage.OutboxRelayRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Outbox 闭环：定时扫表 → 幂等消费（模拟下游）→ markSent。
 * 需 {@code xm.scenario.transaction=memory|jdbc} 且 {@code xm.scenario.outbox-relay.enabled=true}。
 */
@Configuration
@EnableScheduling
@ConditionalOnBean(LocalMessageTxSupport.class)
@ConditionalOnProperty(name = "xm.scenario.outbox-relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayConfig {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayConfig.class);

    @Bean
    public OutboxRelayRunner outboxRelayRunner(LocalMessageTxSupport localMessageTxSupport) {
        IdempotentMessageProcessor processor = new IdempotentMessageProcessor(new InMemoryIdempotencyKeyStore());
        return new OutboxRelayRunner(localMessageTxSupport, msg -> {
            var result = processor.process(String.valueOf(msg.id()), msg.payload(), payload -> {
                log.info("[outbox→consumer] topic={} id={} payload={}", msg.topic(), msg.id(), payload);
                return "ok";
            });
            if (!result.processed()) {
                log.debug("[outbox] duplicate skip messageId={}", msg.id());
            }
        });
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

        @Scheduled(fixedDelayString = "${xm.scenario.outbox-relay.interval-ms:5000}")
        public void tick() {
            int n = runner.relayBatch(50);
            if (n > 0) {
                log.debug("Outbox relay batch sent count={}", n);
            }
        }
    }
}
