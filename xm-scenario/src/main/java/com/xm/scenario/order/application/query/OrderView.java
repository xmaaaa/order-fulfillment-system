package com.xm.scenario.order.application.query;

import com.xm.scenario.order.domain.state.OrderState;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单读模型（CQRS 写读分离）。写走聚合，读走 View/投影。
 * 大厂常见：写库与读库分离、读库可多副本/ES 等。
 */
public record OrderView(
        String orderId,
        String userId,
        OrderState state,
        BigDecimal totalAmount,
        long version,
        List<OrderLineView> lines
) {
    public record OrderLineView(String skuId, int quantity, BigDecimal price, BigDecimal amount) {
    }
}
