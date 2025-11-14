package com.xm.cache;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 客户端
 *
 * @author XM
 * @date 2025/11/7
 */
@Configuration
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
public class RedissonConditionalConfig {

    @Value("${redis.address}")
    private String redisAddress;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress);
        return Redisson.create(config);
    }
}
