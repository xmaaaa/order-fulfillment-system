package com.xm.scenario.order.application.command;

import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.shared.idempotency.IdempotencyKeyStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等的一种实现：用「幂等键」做防重。
 * <p>
 * 幂等 = 多次执行结果一致。常见实现方式有：
 * <ul>
 *   <li>业务键/ID 判断：同一请求键只处理一次，后续返回已缓存结果（本类做法）；</li>
 *   <li>乐观锁：version 不匹配不更新，重复请求 CAS 失败，结果一致；</li>
 *   <li>状态机：已在目标状态不再流转，多次调用状态不变；</li>
 *   <li>Token：一次性或带 TTL 的 token，同一 token 只认第一次。</li>
 * </ul>
 * 本类采用「幂等键 + 首次结果缓存」：同一 idempotencyKey 在 TTL 内只执行一次 createDraft，
 * 后续相同 key 直接返回缓存的 OrderId，不重复下单。
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

    @Override
    public OrderId createDraft(String idempotencyKey, String userId, List<OrderLineDto> lines) {
        return createDraftWithIdempotency(idempotencyKey, userId, lines);
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
