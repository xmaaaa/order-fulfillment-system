package com.xm.scenario.shared.retry;

import java.util.function.Supplier;

/**
 * 重试策略。大厂常见：指数退避、最大次数、可重试异常白名单。
 */
public interface RetryPolicy {

    /**
     * 执行带重试的调用
     *
     * @param task 单次任务
     * @return 结果
     * @throws Exception 最后一次失败抛出的异常
     */
    <T> T execute(Supplier<T> task) throws Exception;

    /** 是否对该异常进行重试 */
    default boolean isRetryable(Throwable t) {
        return true;
    }
}
