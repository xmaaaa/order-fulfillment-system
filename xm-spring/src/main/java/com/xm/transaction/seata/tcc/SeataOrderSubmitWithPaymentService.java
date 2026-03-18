package com.xm.transaction.seata.tcc;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Seata 原生 TCC 编排：使用 @GlobalTransactional + @TwoPhaseBusinessAction。
 * <p>
 * 与 OrderSubmitWithPaymentTccService（学习用 SimpleTccCoordinator）的区别：
 * - 本类：Seata 框架管理全局事务，各参与者用 @TwoPhaseBusinessAction 声明 Try/Confirm/Cancel
 * - 学习版：手写 SimpleTccCoordinator 编排，Seata 仅包一层 begin/commit/rollback
 */
@Service
@ConditionalOnProperty(name = "xm.scenario.tcc", havingValue = "seata")
public class SeataOrderSubmitWithPaymentService {

    private final SeataTccOrderAction orderAction;
    private final SeataTccPaymentAction paymentAction;
    private final SeataTccInventoryAction inventoryAction;

    public SeataOrderSubmitWithPaymentService(SeataTccOrderAction orderAction,
                                              SeataTccPaymentAction paymentAction,
                                              SeataTccInventoryAction inventoryAction) {
        this.orderAction = orderAction;
        this.paymentAction = paymentAction;
        this.inventoryAction = inventoryAction;
    }

    /**
     * 执行：Try(库存预占 -> 支付预创建 -> 订单校验) -> Confirm(订单 markPaid、库存确认、支付确认)
     * 任一步失败，Seata 自动逆序调用各参与者的 rollback。
     */
    @GlobalTransactional(name = "order-submit-pay", rollbackFor = Exception.class)
    public boolean execute(String orderId, BigDecimal amount, String userId) {
        BusinessActionContext ctx = new BusinessActionContext();
        ctx.setXid(RootContext.getXID());

        // 1. 库存预占
        if (!inventoryAction.prepare(ctx, orderId)) {
            throw new RuntimeException("Inventory reserve failed");
        }
        // 2. 支付预创建
        String paymentId = paymentAction.prepare(ctx, orderId);
        if (paymentId == null || paymentId.isBlank()) {
            throw new RuntimeException("Payment prepare failed");
        }
        // 3. 订单校验（Try）
        if (!orderAction.prepare(orderId, paymentId)) {
            throw new RuntimeException("Order prepare failed");
        }
        return true;
    }
}
