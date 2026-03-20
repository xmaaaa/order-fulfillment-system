package com.xm.scenario.transaction.saga;

import java.util.HashMap;
import java.util.Map;

/**
 * Saga 上下文默认实现（可存中间结果供后续步骤/补偿使用）
 */
public class DefaultSagaContext implements SagaOrchestrator.SagaContext {

    private final String orderId;
    private final Map<String, Object> attrs = new HashMap<>();

    public DefaultSagaContext(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String getOrderId() {
        return orderId;
    }

    @Override
    public void set(String key, Object value) {
        attrs.put(key, value);
    }

    @Override
    public Object get(String key) {
        return attrs.get(key);
    }
}
