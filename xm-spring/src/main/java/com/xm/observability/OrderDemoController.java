package com.xm.observability;

import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 可观测性演示接口 — 产生真实的 Trace / Metric / Log 数据。
 *
 * 快速体验：
 *   # 提交订单（正常）
 *   curl -X POST "http://localhost:8888/demo/orders/submit"
 *
 *   # 提交订单（模拟失败）
 *   curl -X POST "http://localhost:8888/demo/orders/submit?fail=true"
 *
 *   # 支付
 *   curl -X POST "http://localhost:8888/demo/orders/{orderId}/pay"
 *
 *   # 取消
 *   curl -X POST "http://localhost:8888/demo/orders/{orderId}/cancel"
 *
 * 数据去哪里看：
 *   Metrics → http://localhost:9090  搜 orders_submitted_total
 *   Grafana  → http://localhost:3000  "Order Fulfillment" dashboard
 *   Traces   → http://localhost:16686 服务 xm-service（需 -DMANAGEMENT_TRACING_ENABLED=true）
 *   Logs     → http://localhost:5601  Data View: xm-service-*  搜 orderId
 */
@RestController
@RequestMapping("/demo/orders")
public class OrderDemoController {

    private static final Logger log = LoggerFactory.getLogger(OrderDemoController.class);

    private final OrderMetrics orderMetrics;

    // 简单内存 map 保存正在处理的订单计时 sample（demo 用，重启丢失）
    private final Map<String, Timer.Sample> pendingSamples = new ConcurrentHashMap<>();

    public OrderDemoController(OrderMetrics orderMetrics) {
        this.orderMetrics = orderMetrics;
    }

    /**
     * 模拟订单提交。
     * 随机耗时 30~200ms 模拟业务处理，fail=true 时模拟失败。
     */
    @PostMapping("/submit")
    public Map<String, String> submit(@RequestParam(defaultValue = "false") boolean fail) {
        String orderId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (fail) {
            orderMetrics.orderSubmittedFailure();
            log.warn("Order submission failed orderId={} reason=simulated_failure", orderId);
            return Map.of("orderId", orderId, "status", "FAILED", "reason", "simulated_failure");
        }

        // 模拟处理延迟
        int delayMs = ThreadLocalRandom.current().nextInt(30, 200);
        try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        Timer.Sample sample = orderMetrics.startProcessing();
        pendingSamples.put(orderId, sample);

        orderMetrics.orderSubmittedSuccess();
        log.info("Order submitted orderId={} delayMs={}", orderId, delayMs);

        return Map.of("orderId", orderId, "status", "SUBMITTED");
    }

    /** 模拟支付成功，停止计时器 */
    @PostMapping("/{orderId}/pay")
    public Map<String, String> pay(@PathVariable String orderId) {
        Timer.Sample sample = pendingSamples.remove(orderId);
        if (sample != null) {
            sample.stop(orderMetrics.processingDuration());
        }
        orderMetrics.orderPaid();
        log.info("Order paid orderId={}", orderId);
        return Map.of("orderId", orderId, "status", "PAID");
    }

    /** 模拟取消订单，停止计时器 */
    @PostMapping("/{orderId}/cancel")
    public Map<String, String> cancel(@PathVariable String orderId) {
        Timer.Sample sample = pendingSamples.remove(orderId);
        if (sample != null) {
            sample.stop(orderMetrics.processingDuration());
        }
        orderMetrics.orderCancelled();
        log.info("Order cancelled orderId={}", orderId);
        return Map.of("orderId", orderId, "status", "CANCELLED");
    }
}
