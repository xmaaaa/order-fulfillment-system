package com.xm.scenario.shared.retry;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 指数退避重试：baseMs * 2^attempt，最大 maxAttempts 次。
 */
public class ExponentialBackoffRetry implements RetryPolicy {

    private final int maxAttempts;
    private final long baseMs;
    private final long maxBackoffMs;

    public ExponentialBackoffRetry(int maxAttempts, long baseMs, long maxBackoffMs) {
        this.maxAttempts = maxAttempts;
        this.baseMs = baseMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    @Override
    public <T> T execute(Supplier<T> task) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return task.get();
            } catch (Exception e) {
                last = e;
                if (!isRetryable(e) || attempt == maxAttempts - 1) {
                    throw e;
                }
                long sleep = Math.min(maxBackoffMs, baseMs * (1L << attempt));
                TimeUnit.MILLISECONDS.sleep(sleep);
            }
        }
        throw last != null ? last : new IllegalStateException("No attempt");
    }
}
