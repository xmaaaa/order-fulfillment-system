package com.xm.scenario.order.application.command;

import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.model.OrderLine;
import com.xm.scenario.order.domain.state.OrderEvent;
import com.xm.scenario.order.domain.state.OrderState;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单应用服务（用例入口）。
 * 后续在此编排：锁 -> 加载聚合 -> 状态机 -> 持久化 -> 发事件/消息表。
 */
public interface OrderCommandService {

    /**
     * 创建草稿订单
     */
    OrderId createDraft(String userId, List<OrderLineDto> lines);

    /**
     * 提交订单（DRAFT -> SUBMITTED）
     */
    void submit(OrderId orderId);

    /**
     * 支付成功回调（SUBMITTED -> PAID），可与分布式事务编排
     */
    void markPaid(OrderId orderId, String paymentId);

    /**
     * 发货（PAID -> SHIPPED）
     */
    void ship(OrderId orderId);

    /**
     * 取消订单
     */
    void cancel(OrderId orderId);

    Order getOrder(OrderId orderId);

    /** 行项 DTO，便于应用层与外部入参 */
    record OrderLineDto(String skuId, int quantity, BigDecimal price) {
        public OrderLine toOrderLine() {
            return new OrderLine(skuId, quantity, price);
        }
    }
}
