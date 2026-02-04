package com.xm.scenario.order.domain.state;

import com.xm.scenario.order.domain.model.Order;

/**
 * 状态机守卫：在允许的流转基础上，再校验业务条件（如金额>0、地址已填）。
 * 不满足则不允许流转，用于硬核业务规则。
 * <p>
 * 设计模式：<br>
 * - <b>Strategy</b>：可插拔的「是否允许流转」策略，Order 依赖此抽象；<br>
 * - <b>Specification</b>：业务规则封装成谓词 (state, event, order) -> boolean，可组合。
 */
@FunctionalInterface
public interface TransitionGuard {

    /**
     * 当前状态、事件、聚合根下是否允许流转
     */
    boolean allow(OrderState current, OrderEvent event, Order order);

    /** 无额外条件 */
    static TransitionGuard none() {
        return (s, e, o) -> true;
    }
}
