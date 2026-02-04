package com.xm.scenario.order.domain.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机：定义合法流转规则。
 * 可后续扩展为从配置/DB 加载。
 * <p>
 * 设计思想：<b>有限状态机 (FSM)</b> + <b>表驱动 (Table-Driven)</b>：用 Map&lt;State, Set&lt;Event&gt;&gt; 表达流转表。
 */
public final class OrderStateMachine {

    private static final Map<OrderState, Set<OrderEvent>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderState.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderState.DRAFT, Set.of(OrderEvent.SUBMIT, OrderEvent.CANCEL));
        ALLOWED_TRANSITIONS.put(OrderState.SUBMITTED, Set.of(OrderEvent.PAY, OrderEvent.CANCEL));
        ALLOWED_TRANSITIONS.put(OrderState.PAID, Set.of(OrderEvent.SHIP, OrderEvent.CANCEL));
        ALLOWED_TRANSITIONS.put(OrderState.SHIPPED, Set.of(OrderEvent.COMPLETE));
        ALLOWED_TRANSITIONS.put(OrderState.COMPLETED, Set.of());
        ALLOWED_TRANSITIONS.put(OrderState.CANCELLED, Set.of());
    }

    /**
     * 返回在 current 状态下执行 event 后的新状态；若不允许则返回 null。
     */
    public static OrderState next(OrderState current, OrderEvent event) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(event)) {
            return null;
        }
        return switch (event) {
            case SUBMIT -> OrderState.SUBMITTED;
            case PAY -> OrderState.PAID;
            case SHIP -> OrderState.SHIPPED;
            case COMPLETE -> OrderState.COMPLETED;
            case CANCEL -> OrderState.CANCELLED;
        };
    }

    public static boolean canTransition(OrderState current, OrderEvent event) {
        return next(current, event) != null;
    }
}
