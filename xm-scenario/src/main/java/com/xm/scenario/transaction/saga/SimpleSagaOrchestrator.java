package com.xm.scenario.transaction.saga;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习用：Saga 编排器，顺序执行步骤，任一步失败则逆序 compensate。
 */
public class SimpleSagaOrchestrator implements SagaOrchestrator {

    @Override
    public boolean execute(String sagaId, List<SagaStep> steps, SagaContext context) {
        int lastCompleted = -1;
        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            if (!step.execute(context)) {
                for (int j = lastCompleted; j >= 0; j--) {
                    steps.get(j).compensate(context);
                }
                return false;
            }
            // 记录已成功执行的步骤索引，用于补偿
            lastCompleted = i;
        }
        return true;
    }
}
