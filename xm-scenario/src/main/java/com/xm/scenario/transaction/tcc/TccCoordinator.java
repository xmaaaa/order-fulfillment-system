package com.xm.scenario.transaction.tcc;

/**
 * TCC 协调器：编排 Try -> Confirm 或 Cancel。
 * 各资源方实现 Try/Confirm/Cancel 接口，协调器按两阶段调用。
 */
public interface TccCoordinator {

    /**
     * 执行 TCC 事务
     *
     * @param context 上下文（订单 ID、支付 ID、预占 ID 等）
     * @return 是否全部 Confirm 成功；若任一 Try 失败则执行已 Try 的 Cancel
     */
    boolean execute(TccContext context);

    /**
     * 注册参与者（Try/Confirm/Cancel）
     */
    void registerParticipant(String name, TccParticipant participant);

    interface TccContext {
        String getOrderId();
        String getPaymentId();
        String getReserveId();
        // 可扩展
    }

    interface TccParticipant {
        boolean tryPhase(TccContext context);
        boolean confirm(TccContext context);
        boolean cancel(TccContext context);
    }
}
