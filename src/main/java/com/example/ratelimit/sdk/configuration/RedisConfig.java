package com.example.ratelimit.sdk.configuration;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.timeout:2000}")
    private int redisTimeout;

    @Value("${redis.database:0}")
    private int redisDatabase;
    
    @Value("${redis.password:}")
    private String redisPassword;

    @Bean
    public RedisClient redisClient() {
        RedisURI redisURI = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withDatabase(redisDatabase)
                .withTimeout(Duration.ofMillis(redisTimeout))
                .build();
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisURI.setPassword(redisPassword);
        }
        
        return RedisClient.create(redisURI);
    }

    @Bean
    public StatefulRedisConnection<String, byte[]> redisConnection(RedisClient redisClient) {
        return redisClient.connect(new RateLimitRedisCodec());
    }

    @Bean
    public RedisAsyncCommands<String, byte[]> redisAsyncCommands(StatefulRedisConnection<String, byte[]> connection) {
        return connection.async();
    }
}
