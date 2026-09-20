package com.ofs.domain.order.application.query;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现（单机/单测）。多机共享用 RedissonOrderViewCache（ofs-app）。
 *
 * <p>惰性过期：只在读到过期条目时才删，没有后台清理线程。单测和 demo 够用；
 * 真要当本地缓存跑生产请换 Caffeine（带容量上限 + 主动淘汰），否则冷 key 会一直占内存。
 */
public class InMemoryOrderViewCache implements OrderViewCache {

    private final Map<String, Holder> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Entry> get(String orderId) {
        Holder holder = store.get(orderId);
        if (holder == null) {
            return Optional.empty();
        }
        if (holder.expireAt() <= System.currentTimeMillis()) {
            // remove(k, v) 而非 remove(k)：避免删掉别的线程刚回填的新值
            store.remove(orderId, holder);
            return Optional.empty();
        }
        return Optional.of(new Entry(holder.view()));
    }

    @Override
    public void put(String orderId, OrderView view, long ttlMs) {
        store.put(orderId, new Holder(view, System.currentTimeMillis() + ttlMs));
    }

    @Override
    public void putAbsent(String orderId, long ttlMs) {
        store.put(orderId, new Holder(null, System.currentTimeMillis() + ttlMs));
    }

    @Override
    public void evict(String orderId) {
        store.remove(orderId);
    }

    /** 当前条目数（含尚未被惰性清理的过期项），仅供测试/观察 */
    public int size() {
        return store.size();
    }

    private record Holder(OrderView view, long expireAt) {
    }
}
