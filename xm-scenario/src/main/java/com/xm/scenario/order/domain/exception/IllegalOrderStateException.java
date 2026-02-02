package com.xm.scenario.order.domain.exception;

import com.xm.scenario.order.domain.state.OrderEvent;
import com.xm.scenario.order.domain.state.OrderState;

/**
 * 非法状态流转时抛出（在应用层或领域服务中根据 transition 结果使用）
 */
public class IllegalOrderStateException extends RuntimeException {

    public IllegalOrderStateException(OrderState current, OrderEvent event) {
        super("Illegal transition: state=" + current + ", event=" + event);
    }
}
