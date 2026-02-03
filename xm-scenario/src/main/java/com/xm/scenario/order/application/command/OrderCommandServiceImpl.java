package com.xm.scenario.order.application.command;

import com.xm.scenario.order.domain.exception.IllegalOrderStateException;
import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.model.OrderRepository;
import com.xm.scenario.order.domain.state.OrderEvent;
import com.xm.scenario.order.domain.state.OrderStateMachineGuards;
import com.xm.scenario.order.domain.state.TransitionGuard;
import com.xm.scenario.shared.event.DomainEventPublisher;
import com.xm.scenario.shared.event.OrderDomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 订单应用服务实现：编排仓储 + 状态机 + 乐观锁 CAS + 可选领域事件发布 + 守卫。
 *
 * @author eddiema
 */
public class OrderCommandServiceImpl implements OrderCommandService {

    private final OrderRepository orderRepository;
    private final TransitionGuard guard;
    private final DomainEventPublisher eventPublisher;

    public OrderCommandServiceImpl(OrderRepository orderRepository) {
        this(orderRepository, OrderStateMachineGuards.defaultGuards(), null);
    }

    public OrderCommandServiceImpl(OrderRepository orderRepository,
                                   TransitionGuard guard,
                                   DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.guard = guard != null ? guard : OrderStateMachineGuards.defaultGuards();
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrderId createDraft(String userId, List<OrderLineDto> lines) {
        if (userId == null || userId.isBlank() || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("userId and lines required");
        }
        OrderId id = new OrderId("ORD-" + UUID.randomUUID());
        Order order = new Order(id, userId, lines.stream().map(OrderLineDto::toOrderLine).toList());
        orderRepository.save(order);
        publish(OrderDomainEvent.TYPE_CREATED, id.getValue(), userId, "{}");
        return id;
    }

    @Override
    public void submit(OrderId orderId) {
        // 查出版本 V
        Order order = requireOrder(orderId);
        if (!order.transition(OrderEvent.SUBMIT, guard)) {
            throw new IllegalOrderStateException(order.getState(), OrderEvent.SUBMIT);
        }
        // transition() 内已 version++，此处 order.version == V+1；持久层 CAS：WHERE version=V 且写入 V+1
        if (!orderRepository.updateVersion(order)) {
            throw new IllegalStateException("Optimistic lock conflict: order " + orderId.getValue());
        }
        publish(OrderDomainEvent.TYPE_SUBMITTED, orderId.getValue(), order.getUserId(), "{}");
    }

    @Override
    public void markPaid(OrderId orderId, String paymentId) {
        Order order = requireOrder(orderId);
        if (!order.transition(OrderEvent.PAY, guard)) {
            throw new IllegalOrderStateException(order.getState(), OrderEvent.PAY);
        }
        if (!orderRepository.updateVersion(order)) {
            throw new IllegalStateException("Optimistic lock conflict: order " + orderId.getValue());
        }
        publish(OrderDomainEvent.TYPE_PAID, orderId.getValue(), order.getUserId(), "{\"paymentId\":\"" + paymentId + "\"}");
    }

    @Override
    public void ship(OrderId orderId) {
        Order order = requireOrder(orderId);
        if (!order.transition(OrderEvent.SHIP, guard)) {
            throw new IllegalOrderStateException(order.getState(), OrderEvent.SHIP);
        }
        if (!orderRepository.updateVersion(order)) {
            throw new IllegalStateException("Optimistic lock conflict: order " + orderId.getValue());
        }
        publish(OrderDomainEvent.TYPE_SHIPPED, orderId.getValue(), order.getUserId(), "{}");
    }

    @Override
    public void cancel(OrderId orderId) {
        Order order = requireOrder(orderId);
        if (!order.transition(OrderEvent.CANCEL, guard)) {
            throw new IllegalOrderStateException(order.getState(), OrderEvent.CANCEL);
        }
        if (!orderRepository.updateVersion(order)) {
            throw new IllegalStateException("Optimistic lock conflict: order " + orderId.getValue());
        }
        publish(OrderDomainEvent.TYPE_CANCELLED, orderId.getValue(), order.getUserId(), "{}");
    }

    @Override
    public Order getOrder(OrderId orderId) {
        return requireOrder(orderId);
    }

    private Order requireOrder(OrderId orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId.getValue());
        }
        return order;
    }

    private void publish(String eventType, String orderId, String userId, String payload) {
        if (eventPublisher == null) return;
        OrderDomainEvent event = new OrderDomainEvent(
                "evt-" + UUID.randomUUID(),
                orderId,
                eventType,
                userId,
                payload,
                Instant.now()
        );
        eventPublisher.publish(event);
    }
}
