package com.xm.scenario.transaction.tcc;

/**
 * 可写 TCC 上下文：Try 阶段产生的 paymentId、reserveId 等由参与者写入，供 Confirm/Cancel 使用。
 */
public class MutableTccContext implements TccCoordinator.TccContext {

    private final String orderId;
    private String paymentId;
    private String reserveId;

    public MutableTccContext(String orderId) {
        this.orderId = orderId;
    }

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

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public void setReserveId(String reserveId) {
        this.reserveId = reserveId;
    }
}
