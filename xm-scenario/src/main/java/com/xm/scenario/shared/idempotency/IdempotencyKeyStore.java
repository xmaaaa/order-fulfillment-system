package com.xm.scenario.shared.idempotency;

/**
 * 幂等键存储。同一 key 在 TTL 内只认为第一次请求有效，用于防重复提交、Exactly-Once 语义。
 * 实现可选：内存、Redis、DB 唯一索引。
 */
public interface IdempotencyKeyStore {

    /**
     * 尝试占用幂等键。同一 key 首次返回 true，后续在 TTL 内返回 false。
     *
     * @param key      业务幂等键（如 clientId:requestId 或 orderId:event）
     * @param ttlMs    占用时长（毫秒），过期后可再次占用
     * @return 是否占用成功（ true = 首次，可继续执行业务；false = 重复，应直接返回上次结果）
     */
    boolean tryAcquire(String key, long ttlMs);

    /**
     * 释放幂等键（可选，用于短时占用后主动释放；多数场景依赖 TTL 即可）
     */
    void release(String key);
}
