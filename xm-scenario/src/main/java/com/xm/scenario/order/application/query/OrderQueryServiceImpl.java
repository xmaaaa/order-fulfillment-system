package com.xm.scenario.order.application.query;

import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.model.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 从仓储投影为 OrderView（单库时写读共用仓储；多读库时可接单独读库/ES）。
 */
public class OrderQueryServiceImpl implements OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Optional<OrderView> getById(OrderId orderId) {
        Order order = orderRepository.findById(orderId);
        return Optional.ofNullable(order).map(this::toView);
    }

    @Override
    public Optional<OrderView> getById(String orderId) {
        return getById(new OrderId(orderId));
    }

    @Override
    public List<OrderView> listByUserId(String userId, int limit) {
        // 当前仓储无按 userId 查询，返回空；生产可接读库 listByUserId
        return new ArrayList<>();
    }

    private OrderView toView(Order order) {
        List<OrderView.OrderLineView> lines = order.getLines().stream()
                .map(l -> new OrderView.OrderLineView(
                        l.getSkuId(), l.getQuantity(), l.getPrice(), l.getAmount()))
                .toList();
        return new OrderView(
                order.getId().getValue(),
                order.getUserId(),
                order.getState(),
                order.getTotalAmount(),
                order.getVersion(),
                lines
        );
    }
}
