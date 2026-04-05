package com.xm.scenario.order;

import com.xm.scenario.order.application.command.OrderCommandService;
import com.xm.scenario.order.application.command.OrderCommandServiceImpl;
import com.xm.scenario.order.domain.service.OrderDomainService;
import com.xm.scenario.order.domain.exception.IllegalOrderStateException;
import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.model.OrderRepository;
import com.xm.scenario.order.domain.state.OrderState;
import com.xm.scenario.order.infrastructure.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderCommandServiceTest {

    private OrderCommandService orderService;
    private OrderRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryOrderRepository();
        orderService = new OrderCommandServiceImpl(new OrderDomainService(repo));
    }

    @Test
    void createDraftAndSubmitAndPayAndShip() {
        OrderId id = orderService.createDraft("user-1", List.of(
                new OrderCommandService.OrderLineDto("SKU-1", 2, new BigDecimal("50.00"))
        ));
        assertNotNull(id.getValue());
        assertTrue(id.getValue().startsWith("ORD-"));

        Order order = orderService.getOrder(id);
        assertEquals(OrderState.DRAFT, order.getState());
        assertEquals(1, order.getLines().size());
        assertEquals(new BigDecimal("100.00"), order.getTotalAmount());

        orderService.submit(id);
        assertEquals(OrderState.SUBMITTED, orderService.getOrder(id).getState());

        orderService.markPaid(id, "PAY-1");
        assertEquals(OrderState.PAID, orderService.getOrder(id).getState());

        orderService.ship(id);
        assertEquals(OrderState.SHIPPED, orderService.getOrder(id).getState());
    }

    @Test
    void illegalTransitionThrows() {
        OrderId id = orderService.createDraft("user-1", List.of(
                new OrderCommandService.OrderLineDto("SKU-1", 1, new BigDecimal("10.00"))
        ));
        assertThrows(IllegalOrderStateException.class, () -> orderService.markPaid(id, "PAY-1")); // DRAFT 不能直接 PAY
    }

    @Test
    void cancelFromDraft() {
        OrderId id = orderService.createDraft("user-1", List.of(
                new OrderCommandService.OrderLineDto("SKU-1", 1, new BigDecimal("10.00"))
        ));
        orderService.cancel(id);
        assertEquals(OrderState.CANCELLED, orderService.getOrder(id).getState());
    }
}
