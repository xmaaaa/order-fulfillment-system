package com.xm.observability;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调用这些接口后，在 /actuator/prometheus 中搜索 demo_ 前缀即可看到样本数据。
 */
@RestController
@RequestMapping("/demo/metrics")
public class DemoMetricsController {

    private final DemoMetrics demoMetrics;

    public DemoMetricsController(DemoMetrics demoMetrics) {
        this.demoMetrics = demoMetrics;
    }

    /** 增加 demo_clicks_total */
    @GetMapping("/click")
    public String click() {
        demoMetrics.incrementClicks();
        return "ok";
    }

    /** 记录一次 demo_simulated_work_seconds_*（内部 sleep 10ms） */
    @GetMapping("/work")
    public String work() {
        demoMetrics.recordWork(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return "ok";
    }

    /** 调整 demo_queue_depth（Gauge） */
    @PostMapping("/queue")
    public int queue(@RequestParam(defaultValue = "1") int delta) {
        return demoMetrics.adjustQueueDepth(delta);
    }
}
