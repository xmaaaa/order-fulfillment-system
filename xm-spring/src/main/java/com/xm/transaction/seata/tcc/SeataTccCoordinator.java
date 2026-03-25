package com.xm.transaction.seata.tcc;

import com.xm.scenario.transaction.tcc.SimpleTccCoordinator;
import com.xm.scenario.transaction.tcc.TccCoordinator;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.tm.api.GlobalTransaction;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 框架用：TCC 协调器，将执行包装在 Seata 全局事务内。
 * 配置 xm.scenario.tcc=seata 且 Seata Server 可用时生效。
 */
@ConditionalOnClass(GlobalTransactionContext.class)
@ConditionalOnProperty(name = "xm.scenario.tcc", havingValue = "seata")
public class SeataTccCoordinator implements TccCoordinator {

    private final SimpleTccCoordinator delegate = new SimpleTccCoordinator();

    @Override
    public void registerParticipant(String name, TccCoordinator.TccParticipant participant) {
        delegate.registerParticipant(name, participant);
    }

    @Override
    public boolean execute(TccCoordinator.TccContext context) {
        GlobalTransaction tx = GlobalTransactionContext.getCurrentOrCreate();
        try {
            tx.begin(60000, "order-submit-pay");
            boolean ok = delegate.execute(context);
            if (ok) {
                tx.commit();
            } else {
                tx.rollback();
            }
            return ok;
        } catch (TransactionException e) {
            rollbackQuietly(tx);
            throw new RuntimeException(e);
        } catch (Throwable e) {
            rollbackQuietly(tx);
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }

    private void rollbackQuietly(GlobalTransaction tx) {
        try {
            tx.rollback();
        } catch (TransactionException ignored) {
        }
    }
}
