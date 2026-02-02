package com.xm.scenario.order.domain.state;

/**
 * 订单状态（值对象，同时作为状态机节点）
 * 流转：DRAFT -> SUBMITTED -> PAID -> SHIPPED -> COMPLETED
 *      任意可终态 -> CANCELLED
 */
public enum OrderState {
    DRAFT,       // 草稿
    SUBMITTED,   // 已提交
    PAID,        // 已支付
    SHIPPED,     // 已发货
    COMPLETED,   // 已完成
    CANCELLED;   // 已取消

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
