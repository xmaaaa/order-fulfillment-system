package com.xm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 订单超时自动取消：扫描间隔与「待支付最长等待时间」。
 */
@ConfigurationProperties(prefix = "xm.scenario.order-timeout")
public class OrderTimeoutProperties {

    /**
     * 是否启用定时扫描。
     */
    private boolean enabled = false;

    /**
     * 扫描间隔（毫秒）。与 {@link org.springframework.scheduling.annotation.Scheduled#fixedDelayString()} 占位符一致。
     */
    private long scanIntervalMs = 60_000L;

    /**
     * SUBMITTED 状态下超过此时长未支付则取消（如 30 分钟）。
     */
    private Duration paymentTimeout = Duration.ofMinutes(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getScanIntervalMs() {
        return scanIntervalMs > 0 ? scanIntervalMs : 60_000L;
    }

    public void setScanIntervalMs(long scanIntervalMs) {
        this.scanIntervalMs = scanIntervalMs > 0 ? scanIntervalMs : 60_000L;
    }

    public Duration getPaymentTimeout() {
        return paymentTimeout;
    }

    public void setPaymentTimeout(Duration paymentTimeout) {
        this.paymentTimeout = paymentTimeout != null && !paymentTimeout.isNegative() && !paymentTimeout.isZero()
                ? paymentTimeout
                : Duration.ofMinutes(30);
    }
}
