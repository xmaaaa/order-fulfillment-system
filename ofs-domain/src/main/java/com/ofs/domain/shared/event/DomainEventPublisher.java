package com.ofs.domain.shared.event;

/**
 * 领域事件发布者：进程内分发，给<b>必须与业务写入同事务</b>的副作用用。
 *
 * <h3>⚠ 当前状态：未启用，无任何订阅者</h3>
 * {@code OrderScenarioConfig} 构造 {@code OrderCommandServiceImpl} 时走的是单参构造，
 * eventPublisher 为 null，所以 {@code publish()} 是空转。<b>不是设计失误，是场景还没出现</b>——
 * 见下面「什么时候启用」。改之前先读完这段，别误以为它在工作。
 *
 * <h3>和 Outbox 的分工</h3>
 * 判据只有一句：<b>这件事失败了，订单状态变更应该跟着回滚吗？</b>
 * <pre>
 *   应该回滚  → 本接口（进程内，同事务）
 *   不该回滚  → Outbox（LocalMessageOrderCommandService → relay → MQ）
 * </pre>
 * 套两个例子：审计日志写不进去，这笔支付<b>应该</b>回滚（合规要求流转必须有迹可循）；
 * 短信发不出去，这笔支付<b>不该</b>回滚（钱都收了）。
 *
 * <table>
 *   <caption>典型分工</caption>
 *   <tr><th></th><th>本接口（同事务）</th><th>Outbox → MQ</th></tr>
 *   <tr><td>适用</td><td>同库、强一致、跨不了进程</td><td>跨服务、最终一致、但不能丢</td></tr>
 *   <tr><td>例</td><td>更新读模型投影表、写状态流转审计表、同库计数</td>
 *       <td>通知服务发短信、履约系统接单、数仓同步、风控</td></tr>
 * </table>
 *
 * <p><b>两者不是并列，是上下游。</b>成熟形态里领域事件是<b>源</b>，MQ 只是其中一个 sink——
 * Outbox 本身就该是本接口的一个订阅者：
 * <pre>
 *   聚合流转 → 记录事件 → publish()
 *                          ├→ 本地订阅者：更新投影表 / 写审计表（同事务）
 *                          └→ Outbox 订阅者：写 outbox 行（同事务）→ relay → MQ → 别的服务
 * </pre>
 *
 * <p><b>为什么不能二选一：</b>全走 Outbox，relay 延迟窗口里的不一致是用户可见的
 * （下单后刷列表看不到 → 以为失败 → 重复下单）；全走进程内，既跨不了服务，
 * 又会在进程崩溃时丢消息——<b>所以凡是要出站的必须经 Outbox，进程内订阅者绝不能直接发 MQ</b>，
 * 那样就把"写库与发消息原子化"这个唯一价值丢掉了。
 *
 * <h3>什么时候启用</h3>
 * 当前仓储是 {@code InMemoryOrderRepository}，没有独立读库、没有审计表，谈不上"同事务"，
 * 所以本地订阅者暂时无事可做。第一个真实用武之地大概率是：
 * <ol>
 *   <li>接 JDBC 仓储后做 CQRS 读模型投影表（{@code listByUserId} 现在还只能返回空列表）；</li>
 *   <li>状态流转审计表 {@code order_state_log}——最便宜，订单系统基本都要。</li>
 * </ol>
 *
 * <p>启用时顺带能解决一个已知盲区：事件现在由应用服务层的装饰器产出，
 * TCC / Saga / OrderTimeoutScheduler 直接调 {@code OrderDomainService}，<b>完全不产生事件</b>。
 * 把产生点下沉到领域层（聚合记录事件），覆盖面问题和这里一起解决——
 * 思路同缓存失效下沉到仓储层，见 {@code docs/缓存-Cache-Aside.md} §5。
 *
 * @author eddiema
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    /** 订阅（内存实现用；Outbox/MQ 由下游消费） */
    void subscribe(String eventType, DomainEventSubscriber subscriber);

    @FunctionalInterface
    interface DomainEventSubscriber {
        void onEvent(DomainEvent event);
    }
}
