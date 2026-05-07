package com.xm.scenario.transaction.localmessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * 本地消息表投递：扫待发送 → 调用出站处理器（如打日志、发 MQ）→ markSent。
 * 处理器抛异常时不标记已发送，便于下次扫描重试。
 */
public class OutboxRelayRunner {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayRunner.class);

    private final LocalMessageTxSupport localMessageTxSupport;
    private final Consumer<LocalMessageTxSupport.PendingMessage> outboundHandler;

    public OutboxRelayRunner(LocalMessageTxSupport localMessageTxSupport,
                             Consumer<LocalMessageTxSupport.PendingMessage> outboundHandler) {
        this.localMessageTxSupport = localMessageTxSupport;
        this.outboundHandler = outboundHandler;
    }

    /**
     * @return 本批成功标记已发送的条数
     */
    public int relayBatch(int limit) {
        List<LocalMessageTxSupport.PendingMessage> pending = localMessageTxSupport.scanPending(limit);
        int sent = 0;
        for (LocalMessageTxSupport.PendingMessage m : pending) {
            try {
                outboundHandler.accept(m);
                localMessageTxSupport.markSent(m.id());
                sent++;
            } catch (Exception e) {
                log.warn("Outbox relay failed for message id={}, topic={}: {}", m.id(), m.topic(), e.getMessage());
            }
        }
        return sent;
    }
}
