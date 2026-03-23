package com.xm.transaction.seata.saga;

import com.xm.scenario.inventory.client.InventoryClient;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.service.OrderDomainService;
import com.xm.scenario.payment.client.PaymentClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Seata Saga 状态机调用的服务：订单提交并支付流程的正向与补偿。
 * JSON 中 ServiceName=orderSubmitSagaAction。
 * order_submit_with_payment.json
 */
@Service("orderSubmitSagaAction")
@ConditionalOnProperty(name = "xm.scenario.saga", havingValue = "seata")
public class OrderSubmitSagaAction {

    private final OrderDomainService orderDomainService;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;

    public OrderSubmitSagaAction(OrderDomainService orderDomainService,
                                 @org.springframework.beans.factory.annotation.Qualifier("scenarioPaymentClient") PaymentClient paymentClient,
                                 @org.springframework.beans.factory.annotation.Qualifier("scenarioInventoryClient") InventoryClient inventoryClient) {
        this.orderDomainService = orderDomainService;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
    }

    /** 正向：预占库存 */
    public boolean reserveInventory(String orderId) {
        var order = orderDomainService.findOrder(new OrderId(orderId)).orElse(null);
        if (order == null) return false;
        List<String> reserveIds = new ArrayList<>();
        for (var line : order.getLines()) {
            String reserveId = orderId + ":seata:saga:inventory:" + line.getSkuId();
            if (!inventoryClient.reserve(line.getSkuId(), line.getQuantity(), "WH01", reserveId)) {
                reserveIds.forEach(inventoryClient::release);
                return false;
            }
            reserveIds.add(reserveId);
        }
        SagaContextHolder.setReserveIds(orderId, reserveIds);
        return true;
    }

    /** 补偿：释放库存 */
    public boolean compensateReserveInventory(String orderId) {
        List<String> ids = SagaContextHolder.getReserveIds(orderId);
        if (ids != null) {
            ids.forEach(inventoryClient::release);
        }
        return true;
    }

    /** 正向：创建支付 */
    public String createPayment(String orderId) {
        String paymentId = paymentClient.createPayment(orderId, BigDecimal.ZERO, "system");
        return paymentId != null && !paymentId.isBlank() ? paymentId : null;
    }

    /** 补偿：取消支付 */
    public boolean compensateCreatePayment(String paymentId) {
        return paymentId == null || paymentClient.cancelOrRefund(paymentId);
    }

    /** 正向：标记订单已支付 */
    public boolean markOrderPaid(String orderId, String paymentId) {
        try {
            orderDomainService.markPaid(new OrderId(orderId), paymentId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 补偿：订单已支付无法简单回滚，仅记录（生产需业务设计） */
    public boolean compensateMarkOrderPaid(String orderId) {
        return true;
    }
}
