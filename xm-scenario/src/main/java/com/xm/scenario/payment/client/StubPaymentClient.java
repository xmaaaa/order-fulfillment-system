package com.xm.scenario.payment.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付防腐层桩实现（学习/单测用）。生产可由 xm-spring 中 Feign 调用真实支付网关。
 */
public class StubPaymentClient implements PaymentClient {

    private final Map<String, Boolean> paid = new ConcurrentHashMap<>();

    @Override
    public String createPayment(String orderId, BigDecimal amount, String userId) {
        String paymentId = "PAY-" + orderId + "-" + System.currentTimeMillis();
        paid.put(paymentId, false);
        return paymentId;
    }

    @Override
    public boolean isPaid(String paymentId) {
        return Boolean.TRUE.equals(paid.get(paymentId));
    }

    @Override
    public boolean cancelOrRefund(String paymentId) {
        paid.remove(paymentId);
        return true;
    }

    /** 桩方法：模拟支付成功（单测用） */
    public void stubMarkPaid(String paymentId) {
        paid.put(paymentId, true);
    }
}
