package com.xm.scenario.transaction.localmessage;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * 本地消息表事务支持：与业务表在同一 DB 事务中写入「待发消息」。
 * 定时任务或 MQ 消费者扫表发送，下游幂等消费。
 */
public interface LocalMessageTxSupport {

    /**
     * 在本地事务中执行业务逻辑并写入待发消息
     *
     * @param businessAction 业务逻辑（如落订单表）
     * @param message        要发送的消息体（如订单已创建事件）
     * @param topic          目标 topic/queue
     */
    void executeInLocalTx(Runnable businessAction, Object message, String topic);

    /**
     * 带返回值的本地事务：先执行业务，用返回值构建消息体，再写入待发消息（同一事务）。
     * 用于 createDraft 等需返回 ID 且消息体含该 ID 的场景。
     */
    default <T> T executeInLocalTxWithResult(Callable<T> action, Function<T, Object> messageBuilder, String topic) {
        try {
            T result = action.call();
            executeInLocalTx(() -> {}, messageBuilder.apply(result), topic);
            return result;
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }

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
