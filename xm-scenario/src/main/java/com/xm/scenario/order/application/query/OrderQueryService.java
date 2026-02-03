package com.xm.scenario.order.application.query;

import com.xm.scenario.order.domain.model.OrderId;

import java.util.List;
import java.util.Optional;

/**
 * 订单查询服务（CQRS 读侧）。可接读库、ES、缓存。
 */
public interface OrderQueryService {

    Optional<OrderView> getById(OrderId orderId);

    Optional<OrderView> getById(String orderId);

    List<OrderView> listByUserId(String userId, int limit);
}
