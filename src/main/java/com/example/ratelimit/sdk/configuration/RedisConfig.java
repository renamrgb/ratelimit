package com.example.ratelimit.sdk.configuration;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.nio.ByteBuffer;
import java.time.Duration;

@Configuration
public class RedisConfig {

    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.redis.host:redis}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.timeout:2000}")
    private int redisTimeout;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Bean
    public RedisClient redisClient() {
        logger.info("Configurando cliente Redis para host: {}, porta: {}, database: {}", 
                redisHost, redisPort, redisDatabase);
        
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withDatabase(redisDatabase)
                .withTimeout(Duration.ofMillis(redisTimeout));
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }
        
        RedisURI redisURI = uriBuilder.build();
        return RedisClient.create(redisURI);
    }

    @Bean
    @Primary
    public StatefulRedisConnection<String, String> redisStringConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    @Bean
    public StatefulRedisConnection<String, byte[]> redisByteArrayConnection(RedisClient redisClient) {
        return redisClient.connect(RedisCodec.of(StringCodec.UTF8, new ByteArrayCodec()));
    }

    @Bean
    public RedisAsyncCommands<String, String> redisAsyncCommands(StatefulRedisConnection<String, String> connection) {
        return connection.async();
    }

    @Configuration
    @Profile("production")
    public static class ProductionRedisConfig {
        
        @Value("${spring.redis.lettuce.pool.max-active:8}")
        private int maxActive;
        
        @Value("${spring.redis.lettuce.pool.max-idle:8}")
        private int maxIdle;
        
        @Value("${spring.redis.lettuce.pool.min-idle:0}")
        private int minIdle;
        
        @Bean
        public String configureProductionSettings() {
            logger.info("Configurando Redis para ambiente de produção");
            logger.info("Pool settings - max-active: {}, max-idle: {}, min-idle: {}", 
                    maxActive, maxIdle, minIdle);
            return "productionRedisConfig";
        }
    }
    
    @Configuration
    @Profile("development")
    public static class DevelopmentRedisConfig {
        
        @Bean
        public String configureDevelopmentSettings() {
            logger.info("Configurando Redis para ambiente de desenvolvimento");
            return "developmentRedisConfig";
        }
    }
    
    @Configuration
    @Profile("test")
    public static class TestRedisConfig {
        
        @Bean
        public String configureTestSettings() {
            logger.info("Configurando Redis para ambiente de testes");
            return "testRedisConfig";
        }
    }
}
