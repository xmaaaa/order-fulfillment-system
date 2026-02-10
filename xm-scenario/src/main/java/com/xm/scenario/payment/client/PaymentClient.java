package com.xm.scenario.payment.client;

import java.math.BigDecimal;

/**
 * 支付上下文防腐层：本模块只依赖此接口，由 xm-spring 或其它模块实现（Feign/HTTP）。
 */
public interface PaymentClient {

    /**
     * 发起支付（预创建/预扣款等，具体由实现决定）
     *
     * @param orderId  订单 ID
     * @param amount   金额
     * @param userId   用户 ID
     * @return 支付流水号或第三方单号
     */
    String createPayment(String orderId, BigDecimal amount, String userId);

    /**
     * 查询支付状态
     *
     * @param paymentId 支付流水号
     * @return 是否已支付成功
     */
    boolean isPaid(String paymentId);

    /**
     * 取消/退款（补偿用）
     *
     * @param paymentId 支付流水号
     * @return 是否成功
     */
    boolean cancelOrRefund(String paymentId);
}
