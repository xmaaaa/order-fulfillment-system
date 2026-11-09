package com.ofs.ai;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PendingActionStoreTest {

    @Test
    void takeSucceedsOnlyOnce() {
        PendingActionStore store = new PendingActionStore();
        String id = store.propose("payOrder", "ORD-1", "pay-001", "支付订单");

        PendingAction first = store.take(id).orElseThrow();
        assertThat(first.action()).isEqualTo("payOrder");
        assertThat(first.orderId()).isEqualTo("ORD-1");
        assertThat(first.paymentId()).isEqualTo("pay-001");
        assertThat(store.take(id)).isEmpty();
        assertThat(store.peek(id)).isEmpty();
    }

    @Test
    void expiredActionCannotBeTaken() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-28T00:00:00Z"));
        PendingActionStore store = new PendingActionStore(new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        });

        String id = store.propose("cancelOrder", "ORD-9", null, "取消订单");
        now.set(now.get().plus(PendingActionStore.TTL).plusSeconds(1));

        assertThat(store.peek(id)).isEmpty();
        assertThat(store.take(id)).isEmpty();
    }
}
