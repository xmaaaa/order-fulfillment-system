package com.xm.scenario.order.domain.service;

import com.xm.scenario.order.domain.exception.IllegalOrderStateException;
import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.model.OrderLine;
import com.xm.scenario.order.domain.model.OrderRepository;
import com.xm.scenario.order.domain.state.OrderEvent;
import com.xm.scenario.order.domain.state.OrderStateMachineGuards;
import com.xm.scenario.order.domain.state.TransitionGuard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 订单领域服务：承载核心业务逻辑，编排聚合与仓储。
 * <p>
 * 分层：App（应用服务）-> Domain（本类）-> Repository（仓储）
 * - App 层：用例入口、锁、事件发布、防腐层调用
 * - Domain 层：状态机、守卫、领域规则、持久化编排
 * - Repository：纯持久化
 */
public class OrderDomainService {

    private final OrderRepository orderRepository;
    private final TransitionGuard guard;
    private final OrderSubmitDomainService submitDomainService;

    public OrderDomainService(OrderRepository orderRepository) {
        this(orderRepository, OrderStateMachineGuards.defaultGuards(), new OrderSubmitDomainService());
    }

    public OrderDomainService(OrderRepository orderRepository,
                              TransitionGuard guard,
                              OrderSubmitDomainService submitDomainService) {
        this.orderRepository = orderRepository;
        this.guard = guard != null ? guard : OrderStateMachineGuards.defaultGuards();
        this.submitDomainService = submitDomainService != null ? submitDomainService : new OrderSubmitDomainService();
    }

    /** 创建草稿订单 */
    public OrderId createDraft(String userId, List<OrderLine> lines) {
        if (userId == null || userId.isBlank() || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("userId and lines required");
        }
        OrderId id = new OrderId("ORD-" + UUID.randomUUID());
        Order order = new Order(id, userId, lines);
        orderRepository.save(order);
        return id;
    }

    /** 提交订单（DRAFT -> SUBMITTED） */
    public void submit(OrderId orderId) {
        Order order = requireOrder(orderId);
        if (!submitDomainService.canSubmit(order)) {
            throw new IllegalStateException("Order cannot be submitted: " + orderId.getValue());
        }
        if (!order.transition(OrderEvent.SUBMIT, guard)) {
            throw new IllegalOrderStateException(order.getState(), OrderEvent.SUBMIT);
        }
        if (!orderRepository.updateVersion(order)) {
            throw new IllegalStateException("Optimistic lock conflict: order " + orderId.getValue());
        }
    }

    /** 标记已支付（SUBMITTED -> PAID） */
    public void markPaid(OrderId orderId, String paymentId) {
        Order order = requireOrder(orderId);
        if (!order.transition(OrderEvent.PAY, guard)) {
            throw new IllegalOrderStateException(order.getState(), OrderEvent.PAY);
        }
        if (!orderRepository.updateVersion(order)) {
            throw new IllegalStateException("Optimistic lock conflict: order " + orderId.getValue());
        }
    }

    /** 发货（PAID -> SHIPPED） */
    public void ship(OrderId orderId) {
        Order order = requireOrder(orderId);
        if (!order.transition(OrderEvent.SHIP, guard)) {
            throw new IllegalOrderStateException(order.getState(), OrderEvent.SHIP);
        }
        if (!orderRepository.updateVersion(order)) {
            throw new IllegalStateException("Optimistic lock conflict: order " + orderId.getValue());
        }
    }

    /** 取消订单 */
    public void cancel(OrderId orderId) {
        Order order = requireOrder(orderId);
        if (!order.transition(OrderEvent.CANCEL, guard)) {
            throw new IllegalOrderStateException(order.getState(), OrderEvent.CANCEL);
        }
        if (!orderRepository.updateVersion(order)) {
            throw new IllegalStateException("Optimistic lock conflict: order " + orderId.getValue());
        }
    }

    /** 加载订单（供应用层或 TCC/Saga 使用），不存在则抛异常 */
    public Order getOrder(OrderId orderId) {
        return requireOrder(orderId);
    }

    /** 加载订单，不存在返回 empty（供 TCC/Saga 参与者使用） */
    public Optional<Order> findOrder(OrderId orderId) {
        return Optional.ofNullable(orderRepository.findById(orderId));
    }

    private Order requireOrder(OrderId orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId.getValue());
        }
        return order;
    }
}
