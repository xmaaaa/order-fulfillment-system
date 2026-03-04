package com.xm.scenario.order.domain.service;

import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.state.OrderState;

import java.math.BigDecimal;

/**
 * 领域服务：提交订单前的校验与准备，跨聚合或需外部信息的逻辑放此处。
 * 单实体能完成的放聚合内，跨实体/需防腐层时用 Domain Service。
 */
public class OrderSubmitDomainService {

    /**
     * 校验订单是否可提交（状态、行数、金额等）。
     * 若有库存预占、地址校验等，可在此或通过端口注入。
     */
    public boolean canSubmit(Order order) {
        if (order == null || order.getState() != OrderState.DRAFT) {
            return false;
        }
        if (order.getLines() == null || order.getLines().isEmpty()) {
            return false;
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        return true;
    }
}
