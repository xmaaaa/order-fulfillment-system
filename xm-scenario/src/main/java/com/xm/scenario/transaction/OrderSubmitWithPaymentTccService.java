package com.xm.scenario.transaction;

import com.xm.scenario.inventory.client.InventoryClient;
import com.xm.scenario.order.domain.service.OrderDomainService;
import com.xm.scenario.payment.client.PaymentClient;
import com.xm.scenario.transaction.tcc.MutableTccContext;
import com.xm.scenario.transaction.tcc.TccCoordinator;
import com.xm.scenario.transaction.tcc.participant.OrderInventoryTccParticipant;
import com.xm.scenario.transaction.tcc.participant.OrderTccParticipant;
import com.xm.scenario.transaction.tcc.participant.PaymentTccParticipant;

import java.math.BigDecimal;

/**
 * 业务用例：提交订单并支付（TCC 模式）。
 * TccCoordinator 可来自学习实现（SimpleTccCoordinator）或框架（Seata），由配置注入。
 */
public class OrderSubmitWithPaymentTccService {

    private final OrderDomainService orderDomainService;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final TccCoordinator tccCoordinator;

    public OrderSubmitWithPaymentTccService(OrderDomainService orderDomainService,
                                            PaymentClient paymentClient,
                                            InventoryClient inventoryClient,
                                            TccCoordinator tccCoordinator) {
        this.orderDomainService = orderDomainService;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
        this.tccCoordinator = tccCoordinator;
        registerParticipants();
    }

    private void registerParticipants() {
        tccCoordinator.registerParticipant("order", new OrderTccParticipant(orderDomainService));
        tccCoordinator.registerParticipant("payment", new PaymentTccParticipant(paymentClient));
        tccCoordinator.registerParticipant("inventory", new OrderInventoryTccParticipant(inventoryClient, orderDomainService));
    }

    /**
     * 执行：Try(预占库存+预创建支付+校验订单) -> Confirm(确认扣减+确认支付+订单PAID)；
     * 任一步失败则 Cancel 已 Try 的。
     */
    public boolean execute(String orderId, BigDecimal amount, String userId) {
        MutableTccContext ctx = new MutableTccContext(orderId);
        return tccCoordinator.execute(ctx);
    }
}
