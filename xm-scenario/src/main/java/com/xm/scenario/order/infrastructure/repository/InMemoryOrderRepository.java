package com.xm.scenario.order.infrastructure.repository;

import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.model.OrderRepository;
import com.xm.scenario.order.domain.state.OrderState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单仓储内存实现（学习/单测用）。生产可替换为 JdbcOrderRepository。
 *
 * @author eddiema
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> store = new ConcurrentHashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.getId().getValue(), order);
    }

    @Override
    public Order findById(OrderId id) {
        Order o = store.get(id.getValue());
        if (o == null) {
            return null;
        }
        // 返回副本，保证乐观锁：先 load 再 transition 再 updateVersion 时，store 里仍是旧 version
        return new Order(o.getId(), o.getUserId(), new ArrayList<>(o.getLines()), o.getState(), o.getVersion(), o.getSubmittedAtEpochMs());
    }

    /**
     * 乐观锁 CAS：用 compute 把「检查 version + 写入」放在同一 key 的原子块内，避免 check-then-act 竞态。
     * 否则两线程都可能通过 version 检查后再先后 put，导致更新丢失。
     */
    @Override
    public boolean updateVersion(Order order) {
        String id = order.getId().getValue();
        long expectedOldVersion = order.getVersion() - 1;
        Order replaced = store.compute(id, (k, existing) -> {
            if (existing == null || existing.getVersion() != expectedOldVersion) {
                return existing;  // 不满足 CAS，保留原值
            }
            return order;  // 满足 CAS，写入新聚合（version 已 +1）
        });
        // 只有真正写入了本次 order 才返回 true
        return replaced == order;
    }

    @Override
    public List<OrderId> findSubmittedOrderIdsOlderThan(long submittedBeforeEpochMs) {
        return store.values().stream()
                .filter(o -> o.getState() == OrderState.SUBMITTED && o.getSubmittedAtEpochMs() > 0 && o.getSubmittedAtEpochMs() < submittedBeforeEpochMs)
                .map(Order::getId)
                .toList();
    }
}
