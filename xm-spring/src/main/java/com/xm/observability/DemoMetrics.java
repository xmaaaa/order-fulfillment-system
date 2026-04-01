package com.xm.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer 示例：Counter / Timer / Gauge。指标名可在 /actuator/prometheus 中查看。
 */
@Component
public class DemoMetrics {

    private final Counter clicks;
    private final Timer simulatedWork;
    private final AtomicInteger queueDepth = new AtomicInteger(0);

    public DemoMetrics(MeterRegistry registry) {
        this.clicks = Counter.builder("demo.clicks")
                .description("Example click counter")
                .tag("source", "demo")
                .register(registry);

        this.simulatedWork = Timer.builder("demo.simulated.work")
                .description("Example timed operation")
                .publishPercentileHistogram()
                .register(registry);

        Gauge.builder("demo.queue.depth", queueDepth, AtomicInteger::get)
                .description("Example fake queue depth")
                .tag("source", "demo")
                .register(registry);
    }

    public void incrementClicks() {
        clicks.increment();
    }

    public void recordWork(Runnable runnable) {
        simulatedWork.record(runnable);
    }

    /** @return 当前队列深度 */
    public int adjustQueueDepth(int delta) {
        return queueDepth.addAndGet(delta);
    }
}
