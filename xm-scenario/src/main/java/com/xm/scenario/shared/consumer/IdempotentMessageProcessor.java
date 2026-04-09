package com.xm.scenario.shared.consumer;

import com.xm.scenario.shared.idempotency.IdempotencyKeyStore;

import java.util.function.Function;

/**
 * 幂等消息处理器：下游消费 MQ 时按 messageId 去重，实现 Exactly-Once 语义。
 * 首次消费时 tryAcquire 成功则执行业务；重复消息直接返回已处理（可缓存首次结果）。
 */
public class IdempotentMessageProcessor {

    /** 默认幂等键 TTL：24 小时，覆盖 MQ 重试窗口 */
    public static final long DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L;

    private final IdempotencyKeyStore idempotencyKeyStore;
    private final long ttlMs;

    public IdempotentMessageProcessor(IdempotencyKeyStore idempotencyKeyStore) {
        this(idempotencyKeyStore, DEFAULT_TTL_MS);
    }

    public IdempotentMessageProcessor(IdempotencyKeyStore idempotencyKeyStore, long ttlMs) {
        this.idempotencyKeyStore = idempotencyKeyStore;
        this.ttlMs = ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS;
    }

    /**
     * 幂等消费：同一 messageId 只处理一次。
     *
     * @param messageId 消息唯一 ID（如 outbox id、MQ messageId）
     * @param payload   消息体
     * @param processor 业务处理逻辑，返回处理结果
     * @return 处理结果；若重复则返回 {@link ConsumeResult#duplicate()}
     */
    public <T> ConsumeResult<T> process(String messageId, Object payload, Function<Object, T> processor) {
        String key = "consume:" + messageId;
        if (!idempotencyKeyStore.tryAcquire(key, ttlMs)) {
            return ConsumeResult.duplicate();
        }
        try {
            T result = processor.apply(payload);
            return ConsumeResult.success(result);
        } catch (Exception e) {
            idempotencyKeyStore.release(key);
            throw e;
        }
    }

    /**
     * 幂等消费（无返回值）
     */
    public ConsumeResult<Void> process(String messageId, Object payload, Runnable processor) {
        return process(messageId, payload, p -> {
            processor.run();
            return null;
        });
    }

    public record ConsumeResult<T>(boolean processed, T result) {
        public static <T> ConsumeResult<T> success(T result) {
            return new ConsumeResult<>(true, result);
        }

        public static <T> ConsumeResult<T> duplicate() {
            return new ConsumeResult<>(false, null);
        }
    }
}
