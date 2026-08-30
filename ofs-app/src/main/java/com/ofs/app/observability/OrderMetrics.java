package com.ofs.app.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 订单业务指标。在 /actuator/prometheus 中以 orders_ 前缀查找。
 *
 * 指标名（Prometheus 格式）：
 *   orders_submitted_total{result="success|failure"}
 *   orders_paid_total
 *   orders_cancelled_total
 *   orders_processing_duration_seconds_{bucket,count,sum,max}
 */
@Component
public class OrderMetrics {

    private final Counter submittedSuccess;
    private final Counter submittedFailure;
    private final Counter paid;
    private final Counter cancelled;
    private final Timer processingDuration;

    public OrderMetrics(MeterRegistry registry) {
        this.submittedSuccess = Counter.builder("orders.submitted")
                .description("Orders submitted")
                .tag("result", "success")
                .register(registry);

        this.submittedFailure = Counter.builder("orders.submitted")
                .description("Orders submitted")
                .tag("result", "failure")
                .register(registry);

        this.paid = Counter.builder("orders.paid")
                .description("Orders paid successfully")
                .register(registry);

        this.cancelled = Counter.builder("orders.cancelled")
                .description("Orders cancelled")
                .register(registry);

        this.processingDuration = Timer.builder("orders.processing.duration")
                .description("Time from order submission to paid/cancelled")
                .publishPercentileHistogram()
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(registry);
    }

    public void orderSubmittedSuccess() { submittedSuccess.increment(); }
    public void orderSubmittedFailure() { submittedFailure.increment(); }
    public void orderPaid()             { paid.increment(); }
    public void orderCancelled()        { cancelled.increment(); }

    /** 在提交时调用，返回 Sample；支付/取消时调用 sample.stop(orderMetrics.processingDuration()) */
    public Timer.Sample startProcessing() { return Timer.start(); }
    public Timer processingDuration()     { return processingDuration; }
}
