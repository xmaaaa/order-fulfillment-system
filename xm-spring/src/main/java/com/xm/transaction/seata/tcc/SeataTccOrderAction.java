package com.xm.transaction.seata.tcc;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * Seata 原生 TCC：订单参与者。
 * Try=校验订单 SUBMITTED，Confirm=markPaid，Cancel=无操作。
 * <p>
 * 使用方式：在 TM 方法上加 @GlobalTransactional，依次调用本接口的 prepare 及各 TCC 的 prepare。
 */
@LocalTCC
public interface SeataTccOrderAction {

    @TwoPhaseBusinessAction(name = "seataTccOrderPrepare", commitMethod = "commit", rollbackMethod = "rollback")
    boolean prepare(@BusinessActionContextParameter(paramName = "orderId") String orderId,
                    @BusinessActionContextParameter(paramName = "paymentId") String paymentId);

    boolean commit(BusinessActionContext context);

    boolean rollback(BusinessActionContext context);
}
