package com.xm.config.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;

import java.util.function.Supplier;

/**
 * Wraps calls with {@link SphU#entry(String)} for Sentinel flow/degrade metrics.
 * Exceptions are traced for exception-ratio degrade rules; {@link BlockException} means blocked by rule.
 */
public final class SentinelClientSupport {

    private SentinelClientSupport() {
    }

    public static <T> T execute(String resource, Supplier<T> supplier) {
        Entry entry = null;
        try {
            entry = SphU.entry(resource);
            return supplier.get();
        } catch (BlockException e) {
            throw new SentinelBlockedException(resource, e);
        } catch (Throwable t) {
            if (!BlockException.isBlockException(t)) {
                Tracer.trace(t);
            }
            if (t instanceof RuntimeException re) {
                throw re;
            }
            if (t instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(t);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    public static void run(String resource, Runnable runnable) {
        execute(resource, () -> {
            runnable.run();
            return null;
        });
    }
}
