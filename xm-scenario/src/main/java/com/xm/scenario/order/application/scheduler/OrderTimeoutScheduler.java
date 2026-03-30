package com.xm.scenario.order.application.scheduler;

import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.model.OrderRepository;
import com.xm.scenario.order.domain.service.OrderDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * 订单超时：扫描 SUBMITTED 且提交时间早于「当前时间 − 待支付窗口」的订单，执行取消。
 * <p>
 * 时间源使用 {@link Clock}，便于测试与多实例时对齐（可换为 {@link Clock#systemUTC()}）。
 */
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderDomainService orderDomainService;
    private final Clock clock;
    private final Duration unpaidMaxWait;

    public OrderTimeoutScheduler(OrderRepository orderRepository,
                                 OrderDomainService orderDomainService,
                                 Clock clock,
                                 Duration unpaidMaxWait) {
        this.orderRepository = orderRepository;
        this.orderDomainService = orderDomainService;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.unpaidMaxWait = unpaidMaxWait != null && !unpaidMaxWait.isNegative() && !unpaidMaxWait.isZero()
                ? unpaidMaxWait
                : Duration.ofMinutes(30);
    }

    /**
     * 执行一次超时取消扫描。
     *
     * @return 本次成功取消的订单数量
     */
    public int run() {
        long now = clock.millis();
        long cutoff = now - unpaidMaxWait.toMillis();
        List<OrderId> toCancel = orderRepository.findSubmittedOrderIdsOlderThan(cutoff);
        int cancelled = 0;
        for (OrderId orderId : toCancel) {
            try {
                orderDomainService.cancel(orderId);
                cancelled++;
                log.info("Order cancelled due to payment timeout: {}", orderId.getValue());
            } catch (Exception e) {
                log.warn("Failed to cancel timed-out order {}: {}", orderId.getValue(), e.getMessage());
            }
        }
        return cancelled;
    }
}
