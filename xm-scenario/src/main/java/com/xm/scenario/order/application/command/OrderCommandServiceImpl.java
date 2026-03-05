package com.xm.scenario.order.application.command;

import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.service.OrderDomainService;
import com.xm.scenario.shared.event.DomainEventPublisher;
import com.xm.scenario.shared.event.OrderDomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 订单应用服务：薄层，负责用例编排、事件发布。
 * <p>
 * 分层：App（本类）-> Domain（OrderDomainService）-> Repository
 * - 本类：入参转换、调用领域服务、发布领域事件
 * - 领域服务：状态机、守卫、持久化编排
 */
public class OrderCommandServiceImpl implements OrderCommandService {

    private final OrderDomainService domainService;
    private final DomainEventPublisher eventPublisher;

    public OrderCommandServiceImpl(OrderDomainService domainService) {
        this(domainService, null);
    }

    public OrderCommandServiceImpl(OrderDomainService domainService,
                                  DomainEventPublisher eventPublisher) {
        this.domainService = domainService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrderId createDraft(String userId, List<OrderLineDto> lines) {
        OrderId id = domainService.createDraft(userId, lines.stream().map(OrderLineDto::toOrderLine).toList());
        publish(OrderDomainEvent.TYPE_CREATED, id.getValue(), userId, "{}");
        return id;
    }

    @Override
    public void submit(OrderId orderId) {
        domainService.submit(orderId);
        Order order = domainService.getOrder(orderId);
        publish(OrderDomainEvent.TYPE_SUBMITTED, orderId.getValue(), order.getUserId(), "{}");
    }

    @Override
    public void markPaid(OrderId orderId, String paymentId) {
        domainService.markPaid(orderId, paymentId);
        Order order = domainService.getOrder(orderId);
        publish(OrderDomainEvent.TYPE_PAID, orderId.getValue(), order.getUserId(), "{\"paymentId\":\"" + paymentId + "\"}");
    }

    @Override
    public void ship(OrderId orderId) {
        domainService.ship(orderId);
        Order order = domainService.getOrder(orderId);
        publish(OrderDomainEvent.TYPE_SHIPPED, orderId.getValue(), order.getUserId(), "{}");
    }

    @Override
    public void cancel(OrderId orderId) {
        domainService.cancel(orderId);
        Order order = domainService.getOrder(orderId);
        publish(OrderDomainEvent.TYPE_CANCELLED, orderId.getValue(), order.getUserId(), "{}");
    }

    @Override
    public Order getOrder(OrderId orderId) {
        return domainService.getOrder(orderId);
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
