package com.xm.config;

import com.xm.scenario.order.application.scheduler.OrderTimeoutScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时扫描超时未支付订单；扫描间隔与 {@link OrderTimeoutProperties#getScanInterval()} 一致。
 */
@Component
@ConditionalOnProperty(name = "xm.scenario.order-timeout.enabled", havingValue = "true")
class OrderTimeoutScheduledJob {

    private final OrderTimeoutScheduler scheduler;

    OrderTimeoutScheduledJob(OrderTimeoutScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Scheduled(fixedDelayString = "${xm.scenario.order-timeout.scan-interval-ms:60000}")
    public void tick() {
        scheduler.run();
    }
}
