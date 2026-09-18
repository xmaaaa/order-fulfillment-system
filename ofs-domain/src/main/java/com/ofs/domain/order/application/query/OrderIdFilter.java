package com.ofs.domain.order.application.query;

/**
 * 订单 ID 存在性过滤器（防缓存穿透第一道防线，典型实现为布隆过滤器）。
 *
 * <p><b>语义必须是「宁可放过，不可错杀」。</b>{@link #mightExist} 返回 false 时，
 * {@link CachedOrderQueryService} 会直接判定订单不存在、连库都不查。所以实现只允许
 * 假阳性（不存在的说成可能存在，代价是多查一次库），<b>绝不允许假阴性</b>（存在的说成
 * 不存在，后果是真实订单被当成 404）。
 *
 * <p>由此推出一条运维前提：<b>所有已存在的订单 ID 都必须先进过滤器</b>。新单靠写侧
 * {@link com.ofs.domain.order.infrastructure.repository.CacheEvictingOrderRepository}
 * 在建单事务提交后实时登记；历史单必须在启用前全量预热。这就是本项目默认
 * {@code ofs.scenario.order-cache.bloom-filter.enabled=false} 的原因——
 * 没预热就开等于给老订单判死刑。
 *
 * <p>另一个已知取舍：布隆过滤器不支持删除元素。订单取消后 ID 仍留在过滤器里，
 * 只会产生「本可短路却去查了库」的假阳性，不影响正确性；要收缩规模只能定期整体重建。
 */
public interface OrderIdFilter {

    /** 登记一个确实存在的订单 ID */
    void add(String orderId);

    /** false = 确定不存在（可直接短路）；true = 可能存在（继续查缓存/库） */
    boolean mightExist(String orderId);
}
