package com.ofs.domain.order.application.command;

import com.ofs.domain.order.domain.model.Order;
import com.ofs.domain.order.domain.model.OrderId;
import com.ofs.domain.order.domain.service.OrderDomainService;
import com.ofs.domain.shared.event.DomainEventPublisher;
import com.ofs.domain.shared.event.OrderDomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 订单应用服务：薄层，负责用例编排、事件发布。
 * <p>
 * 分层：App（本类）-> Domain（OrderDomainService）-> Repository
 * - 本类：入参转换、调用领域服务、发布领域事件
 * - 领域服务：状态机、守卫、持久化编排
 *
 * <p><b>注意 {@link #publish} 当前是空转</b>：{@code OrderScenarioConfig} 用的是单参构造，
 * eventPublisher 为 null。实际在发的事件走的是 outbox
 * （{@code LocalMessageOrderCommandService} 装饰在本类外层）。
 * 进程内事件与 outbox 的分工、以及何时启用，见 {@link DomainEventPublisher} 的类注释。
 */
public class OrderCommandServiceImpl implements OrderCommandService {

    private final OrderDomainService domainService;
    private final DomainEventPublisher eventPublisher;

    /** 不带发布者：进程内领域事件关闭。当前生产装配走的就是这个（见类注释）。 */
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

    /**
     * 发布进程内领域事件。<b>eventPublisher 为 null 时直接返回——当前装配即是如此，所以这是空转。</b>
     * 真正出站的事件由 outbox 负责；两者分工见 {@link DomainEventPublisher}。
     *
     * <p>将来启用时注意：这里的发布点在<b>应用服务层</b>，和 outbox 装饰器同层，
     * 因此继承了同一个盲区——TCC / Saga / OrderTimeoutScheduler 直接调领域服务，不经过这里。
     * 要覆盖全部写路径，得把事件产生点下沉到聚合。
     */
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
