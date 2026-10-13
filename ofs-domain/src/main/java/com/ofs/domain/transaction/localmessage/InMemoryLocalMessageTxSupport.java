package com.ofs.domain.transaction.localmessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 学习用：本地消息表内存实现，无 DB，顺序执行。生产用 {@link JdbcLocalMessageTxSupport}。
 *
 * <p>与 JDBC 实现的两点差异，心里要有数：
 * <ul>
 *   <li><b>没有事务</b>：businessAction 跑完就插消息，中间失败不会回滚已写入的业务数据；</li>
 *   <li><b>没有 (aggregateId, eventType) 唯一约束</b>：JDBC 实现靠这个索引兜底「同一事件只入队一次」，
 *       内存实现不拦。单机 demo 够用，别拿它验证幂等。</li>
 * </ul>
 */
public class InMemoryLocalMessageTxSupport implements LocalMessageTxSupport {

    private final AtomicLong idGen = new AtomicLong(0);
    private final List<Entry> store = new CopyOnWriteArrayList<>();

    @Override
    public void executeInLocalTx(Runnable businessAction, Supplier<OutboxRecord> recordSupplier) {
        businessAction.run();
        OutboxRecord record = recordSupplier.get();
        if (record != null) {
            insertMessage(record);
        }
    }

    private void insertMessage(OutboxRecord record) {
        long id = idGen.incrementAndGet();
        store.add(new Entry(id, record.aggregateId(), record.eventType(),
                record.payload(), record.topic(), System.currentTimeMillis(), false));
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
                .map(e -> new PendingMessage(e.id, e.aggregateId, e.eventType, e.payload, e.topic, e.createdAt))
                .toList();
    }

    private static class Entry {
        final long id;
        final String aggregateId;
        final String eventType;
        final String payload;
        final String topic;
        final long createdAt;
        volatile boolean sent;

        Entry(long id, String aggregateId, String eventType,
              String payload, String topic, long createdAt, boolean sent) {
            this.id = id;
            this.aggregateId = aggregateId;
            this.eventType = eventType;
            this.payload = payload;
            this.topic = topic;
            this.createdAt = createdAt;
            this.sent = sent;
        }
    }
}
