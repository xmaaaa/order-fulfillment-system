package com.xm.scenario.order;

import com.xm.scenario.order.domain.state.OrderEvent;
import com.xm.scenario.order.domain.state.OrderState;
import com.xm.scenario.order.domain.state.OrderStateMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 状态机流转规则单测
 */
class OrderStateMachineTest {

    @Test
    void draftCanSubmitOrCancel() {
        assertTrue(OrderStateMachine.canTransition(OrderState.DRAFT, OrderEvent.SUBMIT));
        assertTrue(OrderStateMachine.canTransition(OrderState.DRAFT, OrderEvent.CANCEL));
        assertEquals(OrderState.SUBMITTED, OrderStateMachine.next(OrderState.DRAFT, OrderEvent.SUBMIT));
        assertEquals(OrderState.CANCELLED, OrderStateMachine.next(OrderState.DRAFT, OrderEvent.CANCEL));
    }

    @Test
    void submittedCanPayOrCancel() {
        assertTrue(OrderStateMachine.canTransition(OrderState.SUBMITTED, OrderEvent.PAY));
        assertEquals(OrderState.PAID, OrderStateMachine.next(OrderState.SUBMITTED, OrderEvent.PAY));
    }

    @Test
    void paidCanShipOrCancel() {
        assertTrue(OrderStateMachine.canTransition(OrderState.PAID, OrderEvent.SHIP));
        assertEquals(OrderState.SHIPPED, OrderStateMachine.next(OrderState.PAID, OrderEvent.SHIP));
    }

    @Test
    void shippedCanComplete() {
        assertTrue(OrderStateMachine.canTransition(OrderState.SHIPPED, OrderEvent.COMPLETE));
        assertEquals(OrderState.COMPLETED, OrderStateMachine.next(OrderState.SHIPPED, OrderEvent.COMPLETE));
    }

    @Test
    void terminalStatesRejectEvents() {
        assertNull(OrderStateMachine.next(OrderState.COMPLETED, OrderEvent.SHIP));
        assertNull(OrderStateMachine.next(OrderState.CANCELLED, OrderEvent.PAY));
    }
}
