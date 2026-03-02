package com.xm.scenario.transaction.localmessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 学习用：本地消息表内存实现，无 DB，顺序执行。生产用 JdbcLocalMessageTxSupport。
 */
public class InMemoryLocalMessageTxSupport implements LocalMessageTxSupport {

    private final AtomicLong idGen = new AtomicLong(0);
    private final List<Entry> store = new CopyOnWriteArrayList<>();

    @Override
    public void executeInLocalTx(Runnable businessAction, Object message, String topic) {
        businessAction.run();
        long id = idGen.incrementAndGet();
        store.add(new Entry(id, message == null ? "" : message.toString(), topic, System.currentTimeMillis(), false));
    }


    @Override
    public void markSent(long messageId) {
        store.stream()
                .filter(e -> e.id == messageId)
                .findFirst()
                .ifPresent(e -> e.sent = true);
    }

    @Override
    public List<PendingMessage> scanPending(int limit) {
        return store.stream()
                .filter(e -> !e.sent)
                .limit(limit)
                .map(e -> new PendingMessage(e.id, e.payload, e.topic, e.createdAt))
                .toList();
    }

    private static class Entry {
        final long id;
        final String payload;
        final String topic;
        final long createdAt;
        volatile boolean sent;

        Entry(long id, String payload, String topic, long createdAt, boolean sent) {
            this.id = id;
            this.payload = payload;
            this.topic = topic;
            this.createdAt = createdAt;
            this.sent = sent;
        }
    }
}
