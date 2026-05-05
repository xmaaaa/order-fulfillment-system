package com.xm.scenario.transaction.localmessage;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OutboxRelayRunnerTest {

    @Test
    void relaysPendingAndMarksSent() {
        var tx = new InMemoryLocalMessageTxSupport();
        tx.executeInLocalTx(() -> {}, () -> "payload-a", "order.created");

        AtomicInteger deliveries = new AtomicInteger();
        var runner = new OutboxRelayRunner(tx, m -> deliveries.incrementAndGet());

        assertEquals(1, runner.relayBatch(10));
        assertEquals(1, deliveries.get());
        assertEquals(0, runner.relayBatch(10), "second batch empty after markSent");
    }

    @Test
    void doesNotMarkSentWhenHandlerFails() {
        var tx = new InMemoryLocalMessageTxSupport();
        tx.executeInLocalTx(() -> {}, () -> "x", "t");

        var runner = new OutboxRelayRunner(tx, m -> {
            throw new IllegalStateException("boom");
        });

        assertEquals(0, runner.relayBatch(10));
        assertEquals(1, tx.scanPending(10).size(), "still pending for retry");
    }
}
