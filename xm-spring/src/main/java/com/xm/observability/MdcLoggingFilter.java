package com.xm.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 每个 HTTP 请求注入以下 MDC 字段，所有日志自动携带：
 *
 *  requestId  — 请求唯一 ID，返回给客户端（X-Request-Id 响应头），用于问题定位
 *  method     — HTTP 方法
 *  path       — 请求路径
 *
 * traceId / spanId 由 Micrometer Tracing 自动注入（xm.scenario.kafka.enabled 无关）。
 * 所有字段均会出现在 JSON 日志的顶层，ELK 可直接过滤。
 */
@Component
@Order(1)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        try {
            MDC.put("requestId", requestId);
            MDC.put("method", request.getMethod());
            MDC.put("path", request.getRequestURI());
            response.setHeader(REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            MDC.remove("method");
            MDC.remove("path");
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        return (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();
    }
}
