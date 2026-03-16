package com.xm.transaction.seata.tcc;

import com.xm.scenario.payment.client.PaymentClient;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Seata TCC 支付参与者实现。
 */
@Service
@ConditionalOnProperty(name = "xm.scenario.tcc", havingValue = "seata")
public class SeataTccPaymentActionImpl implements SeataTccPaymentAction {

    private final PaymentClient paymentClient;

    public SeataTccPaymentActionImpl(@org.springframework.beans.factory.annotation.Qualifier("scenarioPaymentClient") PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @Override
    public String prepare(BusinessActionContext context, String orderId) {
        String paymentId = paymentClient.createPayment(orderId, BigDecimal.ZERO, "system");
        if (paymentId == null || paymentId.isBlank()) return null;
        context.getActionContext().put("paymentId", paymentId);
        return paymentId;
    }

    @Override
    public boolean commit(BusinessActionContext context) {
        return true;
    }

    @Override
    public boolean rollback(BusinessActionContext context) {
        String paymentId = (String) context.getActionContext("paymentId");
        return paymentId == null || paymentClient.cancelOrRefund(paymentId);
    }
}
