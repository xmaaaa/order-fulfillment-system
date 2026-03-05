package com.xm.scenario.order.application.command;

import com.xm.scenario.concurrent.lock.LockPolicy;
import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;

import java.util.List;

/**
 * 订单命令服务 + 锁装饰器：submit/markPaid/ship/cancel 在订单维度的锁内执行。
 * LockPolicy 由配置注入：memory=学习用，redisson=框架。
 */
public class LockedOrderCommandService implements OrderCommandService {

    private static final long LOCK_WAIT_SEC = 5;
    private static final long LOCK_LEASE_SEC = 30;

    private final OrderCommandService delegate;
    private final LockPolicy lockPolicy;

    public LockedOrderCommandService(OrderCommandService delegate, LockPolicy lockPolicy) {
        this.delegate = delegate;
        this.lockPolicy = lockPolicy;
    }

    @Override
    public OrderId createDraft(String userId, List<OrderLineDto> lines) {
        return delegate.createDraft(userId, lines);
    }

    @Override
    public void submit(OrderId orderId) {
        runWithOrderLock(orderId, () -> {
            delegate.submit(orderId);
            return null;
        });
    }

    @Override
    public void markPaid(OrderId orderId, String paymentId) {
        runWithOrderLock(orderId, () -> {
            delegate.markPaid(orderId, paymentId);
            return null;
        });
    }

    @Override
    public void ship(OrderId orderId) {
        runWithOrderLock(orderId, () -> {
            delegate.ship(orderId);
            return null;
        });
    }

    @Override
    public void cancel(OrderId orderId) {
        runWithOrderLock(orderId, () -> {
            delegate.cancel(orderId);
            return null;
        });
    }

    @Override
    public Order getOrder(OrderId orderId) {
        return delegate.getOrder(orderId);
    }

    private void runWithOrderLock(OrderId orderId, java.util.concurrent.Callable<Void> task) {
        try {
            lockPolicy.executeWithOrderLock(orderId.getValue(), task, LOCK_WAIT_SEC, LOCK_LEASE_SEC);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }
}
