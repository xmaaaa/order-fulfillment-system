package com.ofs.domain.shared.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 立即执行（无事务环境 / 单测）。当写操作本身不在事务里时，「提交后」就是「现在」——
 * {@code save()} 一返回数据就可见了，直接执行即正确。
 *
 * <p>生产用 {@code SpringAfterCommitExecutor}（ofs-app），它会在有活跃事务时挂到提交后。
 */
public class ImmediateAfterCommitExecutor implements AfterCommitExecutor {

    private static final Logger log = LoggerFactory.getLogger(ImmediateAfterCommitExecutor.class);

    @Override
    public void afterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        try {
            action.run();
        } catch (RuntimeException ex) {
            // 契约要求：action 失败不能影响调用方
            log.warn("after-commit action failed: {}", ex.getMessage());
        }
    }
}
