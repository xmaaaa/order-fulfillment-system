package com.xm.scenario.shared.consumer;

import com.xm.scenario.shared.idempotency.InMemoryIdempotencyKeyStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdempotentMessageProcessorTest {

    @Test
    void processFirstTimeSucceeds() {
        var store = new InMemoryIdempotencyKeyStore();
        var processor = new IdempotentMessageProcessor(store, 60_000);

        var result = processor.process("msg-1", "payload", p -> "ok");
        assertTrue(result.processed());
        assertEquals("ok", result.result());
    }

    @Test
    void processDuplicateReturnsDuplicate() {
        var store = new InMemoryIdempotencyKeyStore();
        var processor = new IdempotentMessageProcessor(store, 60_000);

        processor.process("msg-2", "payload", p -> "first");
        var result = processor.process("msg-2", "payload", p -> "second");
        assertFalse(result.processed());
        assertNull(result.result());
    }

    @Test
    void processRunnable() {
        var store = new InMemoryIdempotencyKeyStore();
        var processor = new IdempotentMessageProcessor(store, 60_000);
        var counter = new int[1];

        processor.process("msg-3", null, () -> counter[0]++);
        assertEquals(1, counter[0]);

        processor.process("msg-3", null, () -> counter[0]++);
        assertEquals(1, counter[0]);
    }
}
