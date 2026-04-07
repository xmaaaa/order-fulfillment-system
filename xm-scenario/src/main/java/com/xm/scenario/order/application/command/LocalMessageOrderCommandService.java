package com.xm.scenario.order.application.command;

import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.transaction.localmessage.LocalMessageTxSupport;

import java.util.List;

/**
 * 订单命令服务 + 本地消息表装饰器：createDraft 落单与写 outbox 同事务。
 * LocalMessageTxSupport 由配置注入：memory=学习用，jdbc=框架。
 */
public class LocalMessageOrderCommandService implements OrderCommandService {

    private static final String TOPIC_ORDER_CREATED = "order.created";

    private final OrderCommandService delegate;
    private final LocalMessageTxSupport localMessageTxSupport;

    public LocalMessageOrderCommandService(OrderCommandService delegate,
                                          LocalMessageTxSupport localMessageTxSupport) {
        this.delegate = delegate;
        this.localMessageTxSupport = localMessageTxSupport;
    }

    @Override
    public OrderId createDraft(String userId, List<OrderLineDto> lines) {
        return localMessageTxSupport.executeInLocalTxWithResult(
                () -> delegate.createDraft(userId, lines),
                orderId -> "OrderCreated:" + orderId.getValue(),
                TOPIC_ORDER_CREATED
        );
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
}
