package com.xm.scenario.order.domain.state;

import com.xm.scenario.order.domain.model.Order;

import java.math.BigDecimal;

/**
 * 订单状态机守卫实现：支付需金额>0、取消需在可取消状态等。
 * 设计模式：Strategy 的具体实现 + Specification 的组合（defaultGuards 用 && 组合多条规格）。
 */
public final class OrderStateMachineGuards {

    private OrderStateMachineGuards() {
    }

    /** 支付：金额必须大于 0 */
    public static final TransitionGuard PAY_AMOUNT_POSITIVE = (current, event, order) -> {
        if (event != OrderEvent.PAY) return true;
        return order.getTotalAmount().compareTo(BigDecimal.ZERO) > 0;
    };

    /** 提交：至少一行、总价非负 */
    public static final TransitionGuard SUBMIT_HAS_LINES = (current, event, order) -> {
        if (event != OrderEvent.SUBMIT) return true;
        return order.getLines() != null && !order.getLines().isEmpty()
                && order.getTotalAmount().compareTo(BigDecimal.ZERO) >= 0;
    };

    /** 组合：默认使用 PAY + SUBMIT 守卫 */
    public static TransitionGuard defaultGuards() {
        return (current, event, order) ->
                PAY_AMOUNT_POSITIVE.allow(current, event, order)
                        && SUBMIT_HAS_LINES.allow(current, event, order);
    }
}
