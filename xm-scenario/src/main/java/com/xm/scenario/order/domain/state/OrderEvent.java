package com.xm.scenario.order.domain.state;

/**
 * 订单领域事件（触发状态流转）
 */
public enum OrderEvent {
    SUBMIT,      // 提交订单
    PAY,         // 支付成功
    SHIP,        // 发货
    COMPLETE,    // 确认完成
    CANCEL;      // 取消
}
