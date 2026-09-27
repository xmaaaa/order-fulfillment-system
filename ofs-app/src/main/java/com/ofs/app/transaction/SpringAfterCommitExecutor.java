package com.ofs.app.transaction;

import com.ofs.domain.shared.transaction.AfterCommitExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link AfterCommitExecutor} 的 Spring 实现：有活跃事务就挂到提交后，没有就立即执行。
 *
 * <p>用 {@link TransactionSynchronization#afterCommit()} 而不是 {@code afterCompletion(status)}：
 * 前者<b>只在提交时</b>被调用，回滚时根本不触发，正好是我们要的语义（库没变就别动缓存）。
 *
 * <p>注意 {@code afterCommit()} 里抛出的异常会传播给 commit 的调用方，所以这里必须自己兜住——
 * 事务已经提交成功了，一个缓存失效失败不该让调用方以为业务失败了。
 */
public class SpringAfterCommitExecutor implements AfterCommitExecutor {

    private static final Logger log = LoggerFactory.getLogger(SpringAfterCommitExecutor.class);

    @Override
    public void afterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 没有事务：数据已经可见，「提交后」就是现在
            run(action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                run(action);
            }
        });
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("after-commit action failed: {}", ex.getMessage());
        }
    }
}
