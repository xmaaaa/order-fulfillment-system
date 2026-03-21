package com.xm.scenario.transaction;

import com.xm.scenario.inventory.client.InventoryClient;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.service.OrderDomainService;
import com.xm.scenario.payment.client.PaymentClient;
import com.xm.scenario.transaction.saga.DefaultSagaContext;
import com.xm.scenario.transaction.saga.SagaOrchestrator;

import java.math.BigDecimal;
import java.util.List;

/**
 * 业务用例：提交订单并支付（Saga 编排模式）。
 * SagaOrchestrator 可来自学习实现（SimpleSagaOrchestrator）或框架（Seata），由配置注入。
 */
public class OrderSubmitWithPaymentSagaService {

    private final OrderDomainService orderDomainService;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final SagaOrchestrator sagaOrchestrator;

    public OrderSubmitWithPaymentSagaService(OrderDomainService orderDomainService,
                                             PaymentClient paymentClient,
                                             InventoryClient inventoryClient,
                                             SagaOrchestrator sagaOrchestrator) {
        this.orderDomainService = orderDomainService;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    /**
     * 执行：Step1 预占库存 -> Step2 预创建支付 -> Step3 订单 PAID；任一步失败则逆序补偿。
     */
    public boolean execute(String orderId, BigDecimal amount, String userId) {
        DefaultSagaContext ctx = new DefaultSagaContext(orderId);
        return sagaOrchestrator.execute("saga-" + orderId, buildSteps(), ctx);
    }

    private List<SagaOrchestrator.SagaStep> buildSteps() {
        return List.of(
                new SagaOrchestrator.SagaStep() {
                    @Override
                    public String getName() {
                        return "reserveInventory";
                    }

                    @Override
                    public boolean execute(SagaOrchestrator.SagaContext ctx) {
                        var order = orderDomainService.findOrder(new OrderId(ctx.getOrderId())).orElse(null);
                        if (order == null) return false;
                        for (var line : order.getLines()) {
                            String reserveId = ctx.getOrderId() + ":saga:inventory:" + line.getSkuId();
                            if (!inventoryClient.reserve(line.getSkuId(), line.getQuantity(), "WH01", reserveId)) {
                                return false;
                            }
                            ctx.set("reserveId:" + line.getSkuId(), reserveId);
                        }
                        return true;
                    }

                    @Override
                    public boolean compensate(SagaOrchestrator.SagaContext ctx) {
                        var order = orderDomainService.findOrder(new OrderId(ctx.getOrderId())).orElse(null);
                        if (order == null) return true;
                        for (var line : order.getLines()) {
                            String rid = (String) ctx.get("reserveId:" + line.getSkuId());
                            if (rid != null) inventoryClient.release(rid);
                        }
                        return true;
                    }
                },
                new SagaOrchestrator.SagaStep() {
                    @Override
                    public String getName() {
                        return "createPayment";
                    }

                    @Override
                    public boolean execute(SagaOrchestrator.SagaContext ctx) {
                        String paymentId = paymentClient.createPayment(ctx.getOrderId(), BigDecimal.ZERO, "system");
                        if (paymentId == null || paymentId.isBlank()) return false;
                        ctx.set("paymentId", paymentId);
                        return true;
                    }

                    @Override
                    public boolean compensate(SagaOrchestrator.SagaContext ctx) {
                        String pid = (String) ctx.get("paymentId");
                        return pid == null || paymentClient.cancelOrRefund(pid);
                    }
                },
                new SagaOrchestrator.SagaStep() {
                    @Override
                    public String getName() {
                        return "markOrderPaid";
                    }

                    @Override
                    public boolean execute(SagaOrchestrator.SagaContext ctx) {
                        try {
                            orderDomainService.markPaid(new OrderId(ctx.getOrderId()), (String) ctx.get("paymentId"));
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    }

                    @Override
                    public boolean compensate(SagaOrchestrator.SagaContext ctx) {
                        return true;
                    }
                }
        );
    }
}
