package com.ofs.domain.transaction.localmessage;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 本地消息表：业务与 outbox 同事务写入，定时任务扫表发 MQ，下游幂等消费。
 *
 * <p>写入的是一条 {@link OutboxRecord}，而不是「一个字符串 + 一个 topic」——因为 outbox 行
 * 除了消息体本身，还需要<b>聚合根 ID</b> 和<b>事件类型</b>：
 * <ul>
 *   <li>{@code aggregateId} 给 MQ 当分区键，保证同一订单的消息落同一分区、不乱序；</li>
 *   <li>{@code (aggregateId, eventType)} 在 outbox 表上有唯一索引，是「同一订单的同一事件
 *       只入队一次」的兜底（见 {@code db/outbox_schema.sql}）。</li>
 * </ul>
 */
public interface LocalMessageTxSupport {

    /**
     * 一条待写入 outbox 的记录。
     *
     * @param aggregateId 聚合根 ID（订单场景即 orderId），用作 MQ 分区键
     * @param eventType   事件类型，如 {@code OrderPaid}
     * @param payload     消息体（JSON）
     * @param topic       目标 topic
     */
    record OutboxRecord(String aggregateId, String eventType, String payload, String topic) {

        public OutboxRecord {
            if (aggregateId == null || aggregateId.isBlank()) {
                throw new IllegalArgumentException("aggregateId required");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType required");
            }
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("topic required");
            }
            payload = payload == null ? "" : payload;
        }
    }

    /**
     * 事务内执行业务、用返回值构建消息、写入 outbox，三者原子。
     */
    @SuppressWarnings("unchecked")
    default <T> T executeInLocalTxWithResult(Callable<T> action, Function<T, OutboxRecord> recordBuilder) {
        final Object[] resultHolder = new Object[1];
        executeInLocalTx(
                () -> {
                    try {
                        resultHolder[0] = action.call();
                    } catch (Exception e) {
                        if (e instanceof RuntimeException re) throw re;
                        throw new RuntimeException(e);
                    }
                },
                () -> recordBuilder.apply((T) resultHolder[0])
        );
        return (T) resultHolder[0];
    }

    /** 内部：执行业务后调用 recordSupplier 获取消息并写入 outbox */
    void executeInLocalTx(Runnable businessAction, Supplier<OutboxRecord> recordSupplier);

    /**
     * 标记消息已发送（扫表发送成功后调用）
     *
     * @param messageId 本地消息表主键
     */
    void markSent(long messageId);

    /**
     * 扫描待发送消息（定时任务调用）
     *
     * @param limit 每批条数
     * @return 待发送消息列表
     */
    List<PendingMessage> scanPending(int limit);

    record PendingMessage(long id, String aggregateId, String eventType,
                          String payload, String topic, long createdAt) {
    }
}
