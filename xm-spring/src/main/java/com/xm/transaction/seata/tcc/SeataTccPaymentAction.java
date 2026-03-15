package com.xm.transaction.seata.tcc;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * Seata 原生 TCC：支付参与者。Try=预创建，Confirm=无操作，Cancel=退款。
 * prepare 将 paymentId 写入 context 供 rollback 使用，并返回 paymentId 供 TM 传递给后续参与者。
 */
@LocalTCC
public interface SeataTccPaymentAction {

    @TwoPhaseBusinessAction(name = "seataTccPaymentPrepare", commitMethod = "commit", rollbackMethod = "rollback")
    String prepare(BusinessActionContext context,
                   @BusinessActionContextParameter(paramName = "orderId") String orderId);

    boolean commit(BusinessActionContext context);

    boolean rollback(BusinessActionContext context);
}
