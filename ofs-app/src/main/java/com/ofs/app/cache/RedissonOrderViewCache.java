package com.ofs.app.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofs.domain.order.application.query.OrderView;
import com.ofs.domain.order.application.query.OrderViewCache;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link OrderViewCache} 的 Redis 实现（多机共享）。配置 {@code ofs.scenario.cache=redis}
 * 且 {@code redis.enabled=true} 时生效。
 *
 * <p><b>为什么手写 JSON 而不用 Redisson 默认编解码器：</b>默认 codec 存的是二进制，
 * {@code redis-cli GET} 看到一堆乱码；这里配 {@link StringCodec} + Jackson 存成 JSON 字符串，
 * 本地能直接 {@code redis-cli --scan --pattern 'ofs:order:view:*'} 加 {@code GET} 看内容，
 * 观察「冷启动全 miss → 回填 → 命中」很方便。代价是比二进制略大，订单读模型这个体量无所谓。
 *
 * <p>OrderView 是 record，Jackson 2.15 原生支持 record 的序列化与反序列化（走规范构造器），
 * 不需要加注解。但这也意味着<b>改 OrderView 字段等于改缓存格式</b>：老 JSON 反序列化会失败，
 * 处理方式见 {@link #get}。
 */
public class RedissonOrderViewCache implements OrderViewCache {

    private static final Logger log = LoggerFactory.getLogger(RedissonOrderViewCache.class);

    /**
     * 「已知不存在」占位符。用一个不可能是合法 JSON 的字面量，
     * 好和真实值一眼区分开；也别用空串——空串和「写失败留下的空值」分不清。
     */
    static final String ABSENT_MARKER = "__ABSENT__";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    public RedissonOrderViewCache(RedissonClient redissonClient, ObjectMapper objectMapper, String keyPrefix) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "ofs:order:view:" : keyPrefix;
    }

    @Override
    public Optional<Entry> get(String orderId) {
        String raw = bucket(orderId).get();
        if (raw == null) {
            return Optional.empty();
        }
        if (ABSENT_MARKER.equals(raw)) {
            return Optional.of(Entry.absent());
        }
        try {
            return Optional.of(Entry.of(objectMapper.readValue(raw, OrderView.class)));
        } catch (JsonProcessingException ex) {
            // 脏数据或改过 OrderView 字段结构导致的反序列化失败。
            // 顺手删掉再当未命中处理：这样下一次读会回填成新格式，缓存自愈，
            // 不会每次请求都在这里反序列化失败一遍
            log.warn("Corrupt cache entry for orderId={}, dropping it and treating as miss: {}",
                    orderId, ex.getOriginalMessage());
            bucket(orderId).delete();
            return Optional.empty();
        }
    }

    @Override
    public void put(String orderId, OrderView view, long ttlMs) {
        String json;
        try {
            json = objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException ex) {
            // 序列化不了是代码/模型问题，不是运行时抖动，抛出去让上层打点 + 告警
            throw new IllegalStateException("Failed to serialize OrderView for orderId=" + orderId, ex);
        }
        bucket(orderId).set(json, ttlMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void putAbsent(String orderId, long ttlMs) {
        bucket(orderId).set(ABSENT_MARKER, ttlMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void evict(String orderId) {
        bucket(orderId).delete();
    }

    private RBucket<String> bucket(String orderId) {
        return redissonClient.getBucket(keyPrefix + orderId, StringCodec.INSTANCE);
    }
}
