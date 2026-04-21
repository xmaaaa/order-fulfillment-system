package com.xm.config.sentinel;

import com.xm.scenario.payment.client.PaymentClient;

import java.math.BigDecimal;

/**
 * {@link PaymentClient} wrapped with Sentinel resource {@link #RESOURCE}.
 */
public class SentinelPaymentClient implements PaymentClient {

    public static final String RESOURCE = "paymentClient";

    private final PaymentClient delegate;

    public SentinelPaymentClient(PaymentClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public String createPayment(String orderId, BigDecimal amount, String userId) {
        return SentinelClientSupport.execute(RESOURCE, () -> delegate.createPayment(orderId, amount, userId));
    }

    @Override
    public boolean isPaid(String paymentId) {
        return SentinelClientSupport.execute(RESOURCE, () -> delegate.isPaid(paymentId));
    }

    @Override
    public boolean cancelOrRefund(String paymentId) {
        return SentinelClientSupport.execute(RESOURCE, () -> delegate.cancelOrRefund(paymentId));
    }
}
