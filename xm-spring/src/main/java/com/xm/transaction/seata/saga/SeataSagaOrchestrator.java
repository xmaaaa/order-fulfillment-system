package com.xm.transaction.seata.saga;

import com.xm.scenario.transaction.saga.SagaOrchestrator;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seata Saga 编排器：使用 StateMachineEngine 驱动 JSON 状态机。
 * saga=seata 时生效，steps 参数被忽略（由 JSON 定义流程）。
 */
@Component
@ConditionalOnProperty(name = "xm.scenario.saga", havingValue = "seata")
public class SeataSagaOrchestrator implements SagaOrchestrator {

    private static final String STATE_MACHINE_NAME = "orderSubmitWithPayment";
    private static final String TENANT_ID = "default";

    private final StateMachineEngine stateMachineEngine;

    public SeataSagaOrchestrator(StateMachineEngine stateMachineEngine) {
        this.stateMachineEngine = stateMachineEngine;
    }

    @Override
    public boolean execute(String sagaId, List<SagaOrchestrator.SagaStep> steps, SagaOrchestrator.SagaContext context) {
        Map<String, Object> startParams = Map.of("orderId", context.getOrderId());
        var inst = stateMachineEngine.startWithBusinessKey(STATE_MACHINE_NAME, TENANT_ID, sagaId, startParams);
        return inst != null && "SU".equals(String.valueOf(inst.getStatus()));
    }
}
