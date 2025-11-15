package com.xm.cache;

import org.redisson.RedissonRedLock;
import org.redisson.api.GeoPosition;
import org.redisson.api.GeoUnit;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RGeo;
import org.redisson.api.RHyperLogLog;
import org.redisson.api.RLock;
import org.redisson.api.RQueue;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RTopic;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redission 工具类
 *
 * @author XM
 * @date 2025/11/7
 */
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
public class RedissonUtils {

    private RedissonClient redissonClient;

    public RedissonUtils(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    // =======================
    // 1. 限流（令牌桶）
    // =======================
    public boolean tryAcquireRateLimiter(String key, int permitsPerSecond) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        // 初始化限流器
        rateLimiter.trySetRate(RateType.OVERALL, permitsPerSecond, 1, RateIntervalUnit.SECONDS);
        return rateLimiter.tryAcquire();
    }

    // =======================
    // 2. UV 计算（基于 HyperLogLog）
    // =======================
    public void addUV(String key, String userId) {
        RHyperLogLog<String> hyperLogLog = redissonClient.getHyperLogLog(key);
        hyperLogLog.add(userId);
    }

    public long countUV(String key) {
        RHyperLogLog<String> hyperLogLog = redissonClient.getHyperLogLog(key);
        return hyperLogLog.count();
    }

    // =======================
    // 3. GEO 计算
    // =======================
    public void addGeo(String key, double longitude, double latitude, String member) {
        RGeo<String> geo = redissonClient.getGeo(key);
        geo.add(longitude, latitude, member);
    }

    public Double distanceGeo(String key, String member1, String member2, GeoUnit unit) {
        RGeo<String> geo = redissonClient.getGeo(key);
        return geo.dist(member1, member2, unit);
    }

    /**
     * 获取某个成员的地理位置
     */
    public GeoPosition getGeoPosition(String key, String member) {
        RGeo<String> geo = redissonClient.getGeo(key);
        Map<String, GeoPosition> positionMap = geo.pos(member);
        return positionMap.get(member);
    }

    public List<String> radiusGeo(String key, double longitude, double latitude, double radius, GeoUnit unit) {
        RGeo<String> geo = redissonClient.getGeo(key);
        return geo.radius(longitude, latitude, radius, unit);
    }

    // =======================
    // 4. 布隆过滤器
    // =======================
    public void addBloomFilter(String key, String value, long expectedInsertions, double falseProbability) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(key);
        bloomFilter.tryInit(expectedInsertions, falseProbability);
        bloomFilter.add(value);
    }

    public boolean containsBloomFilter(String key, String value) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(key);
        return bloomFilter.contains(value);
    }

    // =======================
    // 5. 简单 KV 操作
    // =======================
    public void set(String key, String value, long ttlSeconds) {
        RBucket<String> bucket = redissonClient.getBucket(key);
        bucket.set(value, ttlSeconds, TimeUnit.SECONDS);
    }

    public String get(String key) {
        RBucket<String> bucket = redissonClient.getBucket(key);
        return bucket.get();
    }

    public boolean delete(String key) {
        return redissonClient.getKeys().delete(key) > 0;
    }

    // =======================
    // 6. 分布式锁
    // =======================
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        RLock lock = redissonClient.getLock(key);
        return lock.tryLock(waitTime, leaseTime, unit);
    }

    /**
     * 获取锁，带等待时间，自动续签, 基于netty时间轮
     *
     * @param key
     * @param waitTime
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public boolean tryLockWithWatchDog(String key, long waitTime, TimeUnit unit) throws InterruptedException {
        RLock lock = redissonClient.getLock(key);
        // leaseTime = -1 使用看门狗自动续签
        return lock.tryLock(waitTime, -1, unit);
    }

    public void unlock(String key) {
        RLock lock = redissonClient.getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 使用 Redisson 实现 RedLock
     *
     * @param lockKey      锁名称
     * @param leaseTime    锁最大持有时间（秒）
     * @param businessTask 业务逻辑 Runnable
     * @return 是否成功获取锁并执行任务
     */
    public boolean executeWithRedLock(String lockKey, long leaseTime, Runnable businessTask) {
        // 需要连接 完全独立的 Redis 实例，最好是不同机器、不同 Redis 进程 ！！！！
        // 此处简单，只创建三个独立锁对象在同一个实例
        RLock lock1 = redissonClient.getLock(lockKey + ":1");
        RLock lock2 = redissonClient.getLock(lockKey + ":2");
        RLock lock3 = redissonClient.getLock(lockKey + ":3");

        // 组合成 RedissonRedLock
        RedissonRedLock redLock = new RedissonRedLock(lock1, lock2, lock3);

        boolean locked = false;
        try {
            // 尝试加锁，最多等待5秒，锁持有leaseTime秒
            locked = redLock.tryLock(5, leaseTime, TimeUnit.SECONDS);
            if (locked) {
                // 成功获取锁，执行业务逻辑
                businessTask.run();
                return true;
            } else {
                System.out.println("未获取到 RedLock，业务未执行");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            // 释放锁（只有持有者才能释放）
            if (locked) {
                redLock.unlock();
            }
        }
    }

    // =======================
    // 7. 消息队列（MQ）
    // =======================

    /**
     * 发布消息到队列（生产者）
     */
    public <T> void enqueue(String queueName, T message) {
        RQueue<T> queue = redissonClient.getQueue(queueName);
        queue.add(message);
    }

    /**
     * 从队列中消费消息（消费者，非阻塞）
     */
    public <T> T dequeue(String queueName) {
        RQueue<T> queue = redissonClient.getQueue(queueName);
        return queue.poll();
    }

    /**
     * 从队列中阻塞获取消息（消费者，等待消息）
     */
    public <T> T blockingDequeue(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        RBlockingQueue<T> queue = redissonClient.getBlockingQueue(queueName);
        return queue.poll(timeout, unit);
    }

    /**
     * 发布/订阅模式 - 发布消息
     */
    public void publish(String topicName, Object message) {
        RTopic topic = redissonClient.getTopic(topicName);
        topic.publish(message);
    }

    /**
     * 发布/订阅模式 - 订阅消息
     *
     * @param topicName 主题名
     * @param type      消息类型
     * @param listener  消息监听器
     * @param <T>       消息类型泛型
     */
    public <T> int subscribe(String topicName, Class<T> type, MessageListener<? extends T> listener) {
        RTopic topic = redissonClient.getTopic(topicName);
        return topic.addListener(type, listener);
    }

}
