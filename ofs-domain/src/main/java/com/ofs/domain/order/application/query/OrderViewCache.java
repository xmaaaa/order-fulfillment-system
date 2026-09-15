package com.ofs.domain.order.application.query;

import java.util.Optional;

/**
 * 订单读模型缓存端口（Cache-Aside）。实现可选：内存（单机/单测）、Redis（多机）。
 *
 * <p>关键约定：{@link #get} 必须能区分「没缓存」和「缓存了『这单不存在』」两种情况——
 * 前者要回源查库，后者应直接返回不存在。这是防缓存穿透的第二道防线（空值缓存），
 * 所以返回的是 {@code Optional<Entry>} 而不是 {@code Optional<OrderView>}：
 * 外层 Optional 表示「有没有命中」，Entry 内部才表示「命中的是值还是不存在」。
 *
 * <p>实现可以抛运行时异常，调用方 {@link CachedOrderQueryService} 会捕获并降级读库——
 * 缓存故障不该让查询整体不可用。
 */
public interface OrderViewCache {

    /**
     * 读缓存。
     *
     * @return empty = 未命中（需回源）；Entry.isAbsent() = 命中「不存在」占位；否则取 Entry.view()
     */
    Optional<Entry> get(String orderId);

    /** 回填缓存值 */
    void put(String orderId, OrderView view, long ttlMs);

    /** 回填「不存在」占位（空值缓存，TTL 应远短于正常值，避免建单后长期读不到） */
    void putAbsent(String orderId, long ttlMs);

    /** 失效单个 key（写操作成功后调用） */
    void evict(String orderId);

    /** 缓存条目：view 为 null 表示「已知不存在」 */
    record Entry(OrderView view) {

        public static Entry of(OrderView view) {
            return new Entry(view);
        }

        public static Entry absent() {
            return new Entry(null);
        }

        public boolean isAbsent() {
            return view == null;
        }
    }
}
