package com.ofs.app.config;

import com.ofs.domain.order.application.scheduler.OrderTimeoutScheduler;
import com.ofs.domain.order.domain.model.OrderRepository;
import com.ofs.domain.order.domain.service.OrderDomainService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * 订单超时：Clock、调度器、配置绑定。定时触发见 {@link OrderTimeoutScheduledJob}。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OrderTimeoutProperties.class)
@ConditionalOnProperty(name = "ofs.scenario.order-timeout.enabled", havingValue = "true")
public class OrderTimeoutConfig {

    @Bean
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OrderTimeoutScheduler orderTimeoutScheduler(OrderRepository orderRepository,
                                                       OrderDomainService orderDomainService,
                                                       Clock systemUtcClock,
                                                       OrderTimeoutProperties properties) {
        return new OrderTimeoutScheduler(
                orderRepository,
                orderDomainService,
                systemUtcClock,
                properties.getPaymentTimeout());
    }
}
