package com.xm.scenario.transaction.tcc.participant;

import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.service.OrderDomainService;
import com.xm.scenario.order.domain.state.OrderState;
import com.xm.scenario.transaction.tcc.TccCoordinator;

/**
 * 学习用：订单 TCC 参与者。Try=校验订单 SUBMITTED，Confirm=调用领域服务 markPaid，Cancel=无操作。
 */
public class OrderTccParticipant implements TccCoordinator.TccParticipant {

    private final OrderDomainService orderDomainService;

    public OrderTccParticipant(OrderDomainService orderDomainService) {
        this.orderDomainService = orderDomainService;
    }

    @Override
    public boolean tryPhase(TccCoordinator.TccContext context) {
        return orderDomainService.findOrder(new OrderId(context.getOrderId()))
                .filter(o -> o.getState() == OrderState.SUBMITTED)
                .isPresent();
    }

    @Override
    public boolean confirm(TccCoordinator.TccContext context) {
        try {
            orderDomainService.markPaid(new OrderId(context.getOrderId()), context.getPaymentId());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean cancel(TccCoordinator.TccContext context) {
        return true;
    }
}
