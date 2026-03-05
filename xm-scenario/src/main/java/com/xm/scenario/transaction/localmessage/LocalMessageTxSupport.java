package com.xm.scenario.transaction.localmessage;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 本地消息表：业务与 outbox 同事务写入，定时任务扫表发 MQ，下游幂等消费。
 */
public interface LocalMessageTxSupport {

    /**
     * 事务内执行业务、用返回值构建消息、写入 outbox，三者原子。
     */
    @SuppressWarnings("unchecked")
    default <T> T executeInLocalTxWithResult(Callable<T> action, Function<T, Object> messageBuilder, String topic) {
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
                () -> messageBuilder.apply((T) resultHolder[0]),
                topic
        );
        return (T) resultHolder[0];
    }

    /** 内部：执行业务后调用 messageSupplier 获取消息并写入 outbox */
    void executeInLocalTx(Runnable businessAction, Supplier<Object> messageSupplier, String topic);

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

    record PendingMessage(long id, String payload, String topic, long createdAt) {
    }
}
