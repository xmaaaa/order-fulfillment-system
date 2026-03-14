package com.xm.transaction.seata.tcc;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * Seata 原生 TCC：库存参与者。Try=预占，Confirm=确认扣减，Cancel=释放。
 */
@LocalTCC
public interface SeataTccInventoryAction {

    @TwoPhaseBusinessAction(name = "seataTccInventoryPrepare", commitMethod = "commit", rollbackMethod = "rollback")
    boolean prepare(BusinessActionContext context,
                    @BusinessActionContextParameter(paramName = "orderId") String orderId);

    boolean commit(BusinessActionContext context);

    boolean rollback(BusinessActionContext context);
}
