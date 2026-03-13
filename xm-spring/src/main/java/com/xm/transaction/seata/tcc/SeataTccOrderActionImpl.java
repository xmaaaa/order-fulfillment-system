package com.xm.transaction.seata.tcc;

import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.state.OrderState;
import com.xm.scenario.order.domain.service.OrderDomainService;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Seata TCC 订单参与者实现。
 */
@Service
@ConditionalOnProperty(name = "xm.scenario.tcc", havingValue = "seata")
public class SeataTccOrderActionImpl implements SeataTccOrderAction {

    private final OrderDomainService orderDomainService;

    public SeataTccOrderActionImpl(OrderDomainService orderDomainService) {
        this.orderDomainService = orderDomainService;
    }

    @Override
    public boolean prepare(String orderId, String paymentId) {
        return orderDomainService.findOrder(new OrderId(orderId))
                .filter(o -> o.getState() == OrderState.SUBMITTED)
                .isPresent();
    }

    @Override
    public boolean commit(BusinessActionContext context) {
        String orderId = (String) context.getActionContext("orderId");
        String paymentId = (String) context.getActionContext("paymentId");
        if (orderId == null) return true;
        try {
            orderDomainService.markPaid(new OrderId(orderId), paymentId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean rollback(BusinessActionContext context) {
        return true;
    }
}
