package com.ofs.domain.shared.transaction;

/**
 * 「事务提交后再执行」的端口。实现可选：Spring 事务同步（ofs-app）、立即执行（无事务场景/单测）。
 *
 * <p><b>为什么需要它：</b>缓存失效这类<b>对外可见的副作用</b>，不能基于一个还没提交的事实去发布。
 * 如果在事务内就把缓存删了：
 * <ul>
 *   <li>删完到提交之间进来的读会查到<b>旧值</b>（事务还没提交）并把它回填，之后一直脏到 TTL；</li>
 *   <li>事务万一回滚，你已经白删了一次——库根本没变，缓存里的值本来是对的。</li>
 * </ul>
 * 所以正确时机是<b>提交之后</b>。这也是生产系统的通行做法（Spring 的
 * {@code @TransactionalEventListener(AFTER_COMMIT)}、或干脆订阅 binlog）。
 *
 * <p>把它做成端口而不是直接在领域层调 Spring 的 {@code TransactionSynchronizationManager}，
 * 是沿用本项目一贯的分层约定：领域层定接口，ofs-app 提供框架实现（同 LockPolicy、IdempotencyKeyStore）。
 */
public interface AfterCommitExecutor {

    /**
     * 在当前事务<b>提交后</b>执行 action；当前没有活跃事务时立即执行。
     *
     * <p>事务回滚则 action <b>不执行</b>——库没变，缓存不需要动。
     *
     * <p>实现必须保证 action 抛异常不会影响事务本身：提交已经完成了，
     * 一个失效动作失败不该反过来把成功的业务搞挂。
     */
    void afterCommit(Runnable action);
}
