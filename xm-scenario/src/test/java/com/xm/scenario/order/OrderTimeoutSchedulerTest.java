package com.xm.scenario.order;

import com.xm.scenario.order.application.command.OrderCommandService;
import com.xm.scenario.order.application.command.OrderCommandServiceImpl;
import com.xm.scenario.order.application.scheduler.OrderTimeoutScheduler;
import com.xm.scenario.order.domain.model.OrderRepository;
import com.xm.scenario.order.domain.service.OrderDomainService;
import com.xm.scenario.order.infrastructure.repository.InMemoryOrderRepository;
import com.xm.scenario.order.domain.state.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderTimeoutSchedulerTest {

    private OrderRepository repo;
    private OrderDomainService domainService;
    private OrderCommandService orderService;

    @BeforeEach
    void setUp() {
        repo = new InMemoryOrderRepository();
        domainService = new OrderDomainService(repo);
        orderService = new OrderCommandServiceImpl(domainService);
    }

    @Test
    void cancelsSubmittedWhenOlderThanUnpaidWindow() throws InterruptedException {
        // 1ms 窗口：提交后略等，submittedAt 早于 cutoff 即视为超时
        var scheduler = new OrderTimeoutScheduler(
                repo, domainService, Clock.systemUTC(), Duration.ofMillis(1));

        var id = orderService.createDraft("user-1", List.of(
                new OrderCommandService.OrderLineDto("SKU-1", 1, new BigDecimal("10"))
        ));
        orderService.submit(id);
        assertEquals(OrderState.SUBMITTED, orderService.getOrder(id).getState());

        Thread.sleep(5);

        int cancelled = scheduler.run();
        assertTrue(cancelled >= 1);
        assertEquals(OrderState.CANCELLED, orderService.getOrder(id).getState());
    }
}
