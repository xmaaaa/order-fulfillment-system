package com.xm.web;

import com.xm.scenario.concurrent.lock.LockPolicy;
import com.xm.scenario.order.application.command.OrderCommandService;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.transaction.OrderSubmitWithPaymentSagaService;
import com.xm.scenario.transaction.OrderSubmitWithPaymentTccService;
import com.xm.transaction.seata.tcc.SeataOrderSubmitWithPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 订单接口：验证 lock、transaction、TCC、Saga 的完整流程。
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    private static final long LOCK_WAIT_SEC = 5;
    private static final long LOCK_LEASE_SEC = 60;

    @Autowired(required = false)
    private OrderCommandService orderCommandService;

    @Autowired(required = false)
    private OrderSubmitWithPaymentTccService orderSubmitWithPaymentTccService;

    @Autowired(required = false)
    private SeataOrderSubmitWithPaymentService seataOrderSubmitWithPaymentService;

    @Autowired(required = false)
    private OrderSubmitWithPaymentSagaService orderSubmitWithPaymentSagaService;

    @Autowired(required = false)
    private LockPolicy lockPolicy;

    @PostMapping("/draft")
    public Map<String, String> createDraft(@RequestBody CreateDraftRequest req) {
        if (orderCommandService == null) {
            throw new IllegalStateException("OrderCommandService not configured");
        }
        List<OrderCommandService.OrderLineDto> lines = req.lines().stream()
                .map(l -> new OrderCommandService.OrderLineDto(l.skuId(), l.quantity(), l.price()))
                .toList();
        OrderId id = orderCommandService.createDraft(req.userId(), lines);
        return Map.of("orderId", id.getValue());
    }

    @PostMapping("/{orderId}/submit")
    public void submit(@PathVariable String orderId) {
        requireService().submit(new OrderId(orderId));
    }

    @PostMapping("/{orderId}/paid")
    public void markPaid(@PathVariable String orderId, @RequestParam String paymentId) {
        requireService().markPaid(new OrderId(orderId), paymentId);
    }

    @PostMapping("/{orderId}/ship")
    public void ship(@PathVariable String orderId) {
        requireService().ship(new OrderId(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public void cancel(@PathVariable String orderId) {
        requireService().cancel(new OrderId(orderId));
    }

    @GetMapping("/{orderId}")
    public Map<String, Object> getOrder(@PathVariable String orderId) {
        var order = requireService().getOrder(new OrderId(orderId));
        return Map.of(
                "orderId", order.getId().getValue(),
                "userId", order.getUserId(),
                "state", order.getState().name(),
                "totalAmount", order.getTotalAmount()
        );
    }

    /** TCC 版：提交并支付。入口加订单锁防并发，Try 仅校验。 */
    @PostMapping("/{orderId}/submit-with-payment-tcc")
    public Map<String, Object> submitWithPaymentTcc(@PathVariable String orderId,
                                                    @RequestParam(defaultValue = "0") BigDecimal amount,
                                                    @RequestParam(defaultValue = "user1") String userId) {
        boolean ok = runWithOrderLockIfPresent(orderId, () -> {
            if (seataOrderSubmitWithPaymentService != null) {
                return seataOrderSubmitWithPaymentService.execute(orderId, amount, userId);
            }
            if (orderSubmitWithPaymentTccService != null) {
                return orderSubmitWithPaymentTccService.execute(orderId, amount, userId);
            }
            throw new IllegalStateException("TCC not configured (set xm.scenario.tcc=learning or seata)");
        });
        return Map.of("success", ok);
    }

    private boolean runWithOrderLockIfPresent(String orderId, Callable<Boolean> task) {
        if (lockPolicy != null) {
            try {
                Boolean result = lockPolicy.executeWithOrderLock(orderId, task, LOCK_WAIT_SEC, LOCK_LEASE_SEC);
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException(e);
            }
        }
        try {
            return Boolean.TRUE.equals(task.call());
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }

    /** Saga 版：提交并支付。saga=learning 用 SimpleSagaOrchestrator；saga=seata 用 Seata 状态机 */
    @PostMapping("/{orderId}/submit-with-payment-saga")
    public Map<String, Object> submitWithPaymentSaga(@PathVariable String orderId,
                                                     @RequestParam(defaultValue = "0") BigDecimal amount,
                                                     @RequestParam(defaultValue = "user1") String userId) {
        if (orderSubmitWithPaymentSagaService == null) {
            throw new IllegalStateException("OrderSubmitWithPaymentSagaService not configured (set xm.scenario.saga)");
        }
        boolean ok = orderSubmitWithPaymentSagaService.execute(orderId, amount, userId);
        return Map.of("success", ok);
    }

    private OrderCommandService requireService() {
        if (orderCommandService == null) {
            throw new IllegalStateException("OrderCommandService not configured");
        }
        return orderCommandService;
    }

    record CreateDraftRequest(String userId, List<LineDto> lines) {
    }

    record LineDto(String skuId, int quantity, BigDecimal price) {
    }
}
