package com.xm.scenario.transaction.tcc.participant;

import com.xm.scenario.payment.client.PaymentClient;
import com.xm.scenario.transaction.tcc.TccCoordinator;

import java.math.BigDecimal;

/**
 * 学习用：支付 TCC 参与者。Try=预创建，Confirm=确认，Cancel=退款。生产可接真实支付网关。
 */
public class PaymentTccParticipant implements TccCoordinator.TccParticipant {

    private final PaymentClient paymentClient;

    public PaymentTccParticipant(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @Override
    public boolean tryPhase(TccCoordinator.TccContext context) {
        String paymentId = paymentClient.createPayment(
                context.getOrderId(), BigDecimal.ZERO, "system");
        if (paymentId == null || paymentId.isBlank()) return false;
        if (context instanceof com.xm.scenario.transaction.tcc.MutableTccContext mutable) {
            mutable.setPaymentId(paymentId);
        }
        return true;
    }

    @Override
    public boolean confirm(TccCoordinator.TccContext context) {
        // 真实场景：调用支付网关 Confirm
        return true;
    }

    @Override
    public boolean cancel(TccCoordinator.TccContext context) {
        return paymentClient.cancelOrRefund(context.getPaymentId());
    }
}
