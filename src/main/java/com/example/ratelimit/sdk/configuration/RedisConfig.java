package com.example.ratelimit.sdk.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConfig {

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.timeout:2000}")
    private int redisTimeout;

    @Value("${redis.pool.max-total:50}")
    private int poolMaxTotal;

    @Value("${redis.pool.max-idle:20}")
    private int poolMaxIdle;

    @Value("${redis.pool.min-idle:5}")
    private int poolMinIdle;

    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(poolMaxTotal);
        poolConfig.setMaxIdle(poolMaxIdle);
        poolConfig.setMinIdle(poolMinIdle);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setJmxEnabled(false);
        
        return new JedisPool(poolConfig, redisHost, redisPort, redisTimeout);
    }
}
