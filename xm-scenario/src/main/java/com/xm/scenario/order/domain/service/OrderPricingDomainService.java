package com.xm.scenario.order.domain.service;

import com.xm.scenario.order.domain.model.Order;

import java.math.BigDecimal;

/**
 * 领域服务：订单定价逻辑。若涉及优惠、税费、多币种等跨行项计算，放此处。
 * 当前简化：直接取聚合内 totalAmount；可扩展为折扣、税费等。
 */
public class OrderPricingDomainService {

    /**
     * 计算订单应付金额（可扩展：折扣、税费、运费）。
     */
    public BigDecimal calculatePayableAmount(Order order) {
        if (order == null) return BigDecimal.ZERO;
        return order.getTotalAmount();
    }
}
