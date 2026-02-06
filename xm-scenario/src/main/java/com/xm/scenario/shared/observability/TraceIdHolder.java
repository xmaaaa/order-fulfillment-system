package com.xm.scenario.shared.observability;

import java.util.Optional;

/**
 * 链路追踪 ID 持有器（ThreadLocal / MDC）。
 * 大厂标配：请求入口设 TraceId，日志与下游调用透传，便于排查。
 * 实现可选：ThreadLocal 单机；生产可接 SLF4J MDC 或 OpenTelemetry Context。
 *
 * @author eddiema
 */
public final class TraceIdHolder {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceIdHolder() {
    }

    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static Optional<String> get() {
        return Optional.ofNullable(TRACE_ID.get());
    }

    public static String getOrGenerate() {
        String id = TRACE_ID.get();
        if (id == null || id.isBlank()) {
            id = "trace-" + System.currentTimeMillis() + "-" + Thread.currentThread().threadId();
            TRACE_ID.set(id);
        }
        return id;
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
