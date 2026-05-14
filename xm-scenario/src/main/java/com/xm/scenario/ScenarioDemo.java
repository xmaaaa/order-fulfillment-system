package com.xm.scenario;

import com.xm.scenario.order.application.command.OrderCommandService;
import com.xm.scenario.order.application.command.OrderCommandServiceImpl;
import com.xm.scenario.order.domain.service.OrderDomainService;
import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.model.OrderRepository;
import com.xm.scenario.order.domain.state.OrderState;
import com.xm.scenario.order.infrastructure.repository.InMemoryOrderRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 端到端演示：创建草稿 -> 提交 -> 标记支付 -> 发货，展示状态机流转。
 * 运行方式：在 IDE 中运行 main，或 mvn exec:java -pl xm-scenario -Dexec.mainClass="com.xm.scenario.ScenarioDemo"
 */
public class ScenarioDemo {

    public static void main(String[] args) {
        OrderRepository repo = new InMemoryOrderRepository();
        OrderCommandService orderService = new OrderCommandServiceImpl(new OrderDomainService(repo));

        OrderCommandService.OrderLineDto line1 = new OrderCommandService.OrderLineDto("SKU-001", 2, new BigDecimal("99.00"));
        OrderCommandService.OrderLineDto line2 = new OrderCommandService.OrderLineDto("SKU-002", 1, new BigDecimal("199.00"));

        // 1. 创建草稿
        OrderId orderId = orderService.createDraft("user-1", List.of(line1, line2));
        System.out.println("Created order: " + orderId.getValue());

        Order order = orderService.getOrder(orderId);
        System.out.println("  state=" + order.getState() + ", total=" + order.getTotalAmount());

        // 2. 提交
        orderService.submit(orderId);
        order = orderService.getOrder(orderId);
        System.out.println("After submit: state=" + order.getState());

        // 3. 支付成功
        orderService.markPaid(orderId, "PAY-001");
        order = orderService.getOrder(orderId);
        System.out.println("After pay: state=" + order.getState());

        // 4. 发货
        orderService.ship(orderId);
        order = orderService.getOrder(orderId);
        System.out.println("After ship: state=" + order.getState());

        // 5. 若再发 PAY 事件会抛 IllegalOrderStateException（可注释掉试取消流程）
        // orderService.cancel(orderId);
        // order = orderService.getOrder(orderId);
        // System.out.println("After cancel: state=" + order.getState());

        System.out.println("Done. Final state=" + order.getState() + " (expected " + OrderState.SHIPPED + ")");
    }
}
