package com.xm.scenario.transaction.saga;

import java.util.List;

/**
 * Saga 编排器：按步骤执行，任一步失败则按逆序执行补偿。
 * 还有一种 Choreography 的形式, 通过event-driven 但是不易跟踪
 */
public interface SagaOrchestrator {

    /**
     * 执行 Saga
     *
     * @param sagaId   Saga 实例 ID
     * @param steps    步骤列表（顺序执行）
     * @param context  上下文
     * @return 是否全部成功；若失败则已执行步骤的 compensate 会被调用
     */
    boolean execute(String sagaId, List<SagaStep> steps, SagaContext context);

    interface SagaStep {
        String getName();
        boolean execute(SagaContext context);
        boolean compensate(SagaContext context);
    }

    interface SagaContext {
        String getOrderId();
        void set(String key, Object value);
        Object get(String key);
    }
}
