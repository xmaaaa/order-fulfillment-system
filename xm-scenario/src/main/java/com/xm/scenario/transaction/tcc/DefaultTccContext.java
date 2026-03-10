package com.xm.scenario.transaction.tcc;

/**
 * TCC 上下文默认实现（订单场景）
 */
public record DefaultTccContext(String orderId, String paymentId, String reserveId) implements TccCoordinator.TccContext {

    @Override
    public String getOrderId() {
        return orderId;
    }

    @Override
    public String getPaymentId() {
        return paymentId;
    }

    @Override
    public String getReserveId() {
        return reserveId;
    }
}
