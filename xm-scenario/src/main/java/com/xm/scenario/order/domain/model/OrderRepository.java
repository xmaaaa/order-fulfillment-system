package com.xm.scenario.order.domain.model;

/**
 * 订单仓储（接口在领域层，实现在 infrastructure）
 */
public interface OrderRepository {

    void save(Order order);

    Order findById(OrderId id);

    boolean updateVersion(Order order); // 乐观锁更新
}
