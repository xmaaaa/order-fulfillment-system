package com.xm.scenario.order.application.command;

import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.shared.idempotency.IdempotencyKeyStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等装饰器：同一 idempotencyKey 在 TTL 内只执行一次，后续直接返回已缓存结果。
 * 大厂标配：防重复提交、Exactly-Once 语义。
 *
 * @author eddiema
 */
public class IdempotentOrderCommandService implements OrderCommandService {

    private final OrderCommandService delegate;
    private final IdempotencyKeyStore idempotencyKeyStore;
    private final long ttlMs;

    /** 缓存：idempotencyKey -> createDraft 返回的 OrderId（简化示例，生产可缓存完整响应） */
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

    public IdempotentOrderCommandService(OrderCommandService delegate,
                                         IdempotencyKeyStore idempotencyKeyStore,
                                         long ttlMs) {
        this.delegate = delegate;
        this.idempotencyKeyStore = idempotencyKeyStore;
        this.ttlMs = ttlMs;
    }

    @Override
    public OrderId createDraft(String userId, List<OrderLineDto> lines) {
        return createDraftWithIdempotency(null, userId, lines);
    }

    /**
     * 带幂等键的创建：同一 key 在 TTL 内返回同一 OrderId。
     */
    public OrderId createDraftWithIdempotency(String idempotencyKey, String userId, List<OrderLineDto> lines) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (!idempotencyKeyStore.tryAcquire(idempotencyKey, ttlMs)) {
                CachedResult r = cache.get(idempotencyKey);
                if (r != null && r.orderId != null) {
                    return r.orderId;
                }
                throw new IllegalStateException("Duplicate request: " + idempotencyKey);
            }
        }
        OrderId id = delegate.createDraft(userId, lines);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cache.put(idempotencyKey, new CachedResult(id, System.currentTimeMillis() + ttlMs));
        }
        return id;
    }

    @Override
    public void submit(OrderId orderId) {
        delegate.submit(orderId);
    }

    @Override
    public void markPaid(OrderId orderId, String paymentId) {
        delegate.markPaid(orderId, paymentId);
    }

    @Override
    public void ship(OrderId orderId) {
        delegate.ship(orderId);
    }

    @Override
    public void cancel(OrderId orderId) {
        delegate.cancel(orderId);
    }

    @Override
    public Order getOrder(OrderId orderId) {
        return delegate.getOrder(orderId);
    }

    private record CachedResult(OrderId orderId, long expireAt) {
    }
}
