package com.ofs.domain.order.application.command;

import com.ofs.domain.order.domain.model.Order;
import com.ofs.domain.order.domain.model.OrderId;
import com.ofs.domain.order.domain.model.OrderRepository;
import com.ofs.domain.shared.event.OrderDomainEvent;
import com.ofs.domain.shared.event.OrderEventPayloads;
import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport;
import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport.OutboxRecord;

import java.util.List;

/**
 * 订单命令服务 + 本地消息表装饰器：<b>每一次状态流转</b>都把业务写入与 outbox 消息放进同一事务。
 * LocalMessageTxSupport 由配置注入：memory=学习用，jdbc=框架。
 *
 * <p>五个流转各发一个 topic：
 * <pre>
 *   createDraft → order.created    OrderCreated
 *   submit      → order.submitted  OrderSubmitted
 *   markPaid    → order.paid       OrderPaid      （payload 带 paymentId）
 *   ship        → order.shipped    OrderShipped
 *   cancel      → order.cancelled  OrderCancelled
 * </pre>
 *
 * <p><b>一个 topic 一种事件</b>，而不是所有事件挤进一个 order.events：下游各订各的，
 * 通知服务只订 order.paid，不用收下全部再过滤。代价是<b>跨 topic 没有顺序保证</b>。
 * Kafka 的顺序保证本来也只到<b>分区内</b>：outbox 记录带 aggregateId，Kafka 按它分区，
 * 同一订单固定落同一分区——但当前每单每 topic 只有一条消息，所以 topic 内其实没什么可排的。
 * 下游判断「这条是不是迟到的旧消息」应该用 payload 里的 {@code version}，别赌传输层的顺序。
 *
 * <p><b>覆盖范围的实话</b>：本装饰器只包住走 {@code OrderCommandService} 的写。
 * TCC / Saga / OrderTimeoutScheduler 直接调 {@code OrderDomainService}，<b>不会</b>产生这些事件
 * （和缓存失效当初遇到的是同一个问题，那边靠下沉到仓储层解决）。要让事件覆盖全部写路径，
 * 得把事件产生点下沉到领域层——见 README「Outbox pipeline」的 coverage caveat。
 */
public class LocalMessageOrderCommandService implements OrderCommandService {

    static final String TOPIC_ORDER_CREATED = "order.created";
    static final String TOPIC_ORDER_SUBMITTED = "order.submitted";
    static final String TOPIC_ORDER_PAID = "order.paid";
    static final String TOPIC_ORDER_SHIPPED = "order.shipped";
    static final String TOPIC_ORDER_CANCELLED = "order.cancelled";

    private final OrderCommandService delegate;
    private final LocalMessageTxSupport localMessageTxSupport;

    /**
     * 只用来在事务内把刚写完的订单读回来，取 userId 和 version 放进事件。
     *
     * <p>用仓储而不是 {@code OrderQueryService}：读侧带 Cache-Aside，在事务内走它会把
     * <b>尚未提交</b>的订单回填进缓存，事务一旦回滚就留下一个从没存在过的值。
     * 这正是 {@code CacheEvictingOrderRepository} 类注释里点名的那个边界，别去踩。
     */
    private final OrderRepository orderRepository;

    public LocalMessageOrderCommandService(OrderCommandService delegate,
                                          LocalMessageTxSupport localMessageTxSupport,
                                          OrderRepository orderRepository) {
        this.delegate = delegate;
        this.localMessageTxSupport = localMessageTxSupport;
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderId createDraft(String userId, List<OrderLineDto> lines) {
        return localMessageTxSupport.executeInLocalTxWithResult(
                () -> delegate.createDraft(userId, lines),
                orderId -> {
                    Snapshot s = snapshot(orderId);
                    return record(orderId, OrderDomainEvent.TYPE_CREATED, TOPIC_ORDER_CREATED,
                            OrderEventPayloads.created(orderId.getValue(), s.userId(), s.version()));
                }
        );
    }

    @Override
    public void submit(OrderId orderId) {
        localMessageTxSupport.executeInLocalTx(
                () -> delegate.submit(orderId),
                () -> {
                    Snapshot s = snapshot(orderId);
                    return record(orderId, OrderDomainEvent.TYPE_SUBMITTED, TOPIC_ORDER_SUBMITTED,
                            OrderEventPayloads.submitted(orderId.getValue(), s.userId(), s.version()));
                }
        );
    }

    @Override
    public void markPaid(OrderId orderId, String paymentId) {
        localMessageTxSupport.executeInLocalTx(
                () -> delegate.markPaid(orderId, paymentId),
                () -> {
                    Snapshot s = snapshot(orderId);
                    return record(orderId, OrderDomainEvent.TYPE_PAID, TOPIC_ORDER_PAID,
                            OrderEventPayloads.paid(orderId.getValue(), s.userId(), s.version(), paymentId));
                }
        );
    }

    @Override
    public void ship(OrderId orderId) {
        localMessageTxSupport.executeInLocalTx(
                () -> delegate.ship(orderId),
                () -> {
                    Snapshot s = snapshot(orderId);
                    return record(orderId, OrderDomainEvent.TYPE_SHIPPED, TOPIC_ORDER_SHIPPED,
                            OrderEventPayloads.shipped(orderId.getValue(), s.userId(), s.version()));
                }
        );
    }

    @Override
    public void cancel(OrderId orderId) {
        localMessageTxSupport.executeInLocalTx(
                () -> delegate.cancel(orderId),
                () -> {
                    Snapshot s = snapshot(orderId);
                    return record(orderId, OrderDomainEvent.TYPE_CANCELLED, TOPIC_ORDER_CANCELLED,
                            OrderEventPayloads.cancelled(orderId.getValue(), s.userId(), s.version()));
                }
        );
    }

    private OutboxRecord record(OrderId orderId, String eventType, String topic, String payload) {
        return new OutboxRecord(orderId.getValue(), eventType, payload, topic);
    }

    /**
     * 把刚流转完的订单读回来。这段跑在 {@code executeInLocalTx} 里、业务动作<b>之后</b>，
     * 所以拿到的 version 是流转<b>后</b>的值（聚合 transition() 时 version++）。
     *
     * <p>读不到就抛——此时业务写刚刚成功，读不回来说明仓储有问题。让事务回滚是对的：
     * outbox 模式的全部意义就是「业务写入与消息入队同生共死」，宁可整笔回滚，
     * 也不能提交一条字段残缺的事件给下游。
     */
    private Snapshot snapshot(OrderId orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new IllegalStateException(
                    "Order vanished right after a successful write, cannot build event: " + orderId.getValue());
        }
        return new Snapshot(order.getUserId(), order.getVersion());
    }

    private record Snapshot(String userId, long version) {
    }
}
