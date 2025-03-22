package com.ratelimit.adapters.spring;

import com.ratelimit.adapters.inmemory.InMemoryBucketStore;
import com.ratelimit.adapters.redis.LettuceRedisClient;
import com.ratelimit.adapters.redis.RedisBucketStore;
import com.ratelimit.builder.RateLimiterBuilder;
import com.ratelimit.core.RateLimiter;
import com.ratelimit.core.model.RateLimitConfig;
import com.ratelimit.spi.storage.BucketStore;
import com.ratelimit.util.SimpleKeyGenerator;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuração automática do Spring para a SDK de rate limiting.
 * Registra automaticamente os beans necessários com base nas propriedades do application.yml.
 */
@Configuration
@ConditionalOnClass(RateLimiter.class)
public class RateLimitAutoConfiguration {
    
    @Value("${ratelimit.enabled:true}")
    private boolean enabled;
    
    @Value("${ratelimit.default-limit:100}")
    private long defaultLimit;
    
    @Value("${ratelimit.default-duration:60}")
    private long defaultDurationSeconds;
    
    @Value("${ratelimit.greedy:false}")
    private boolean greedy;
    
    /**
     * Configura o RateLimiter principal.
     * 
     * @param bucketStore armazenamento de buckets a ser usado
     * @return instância configurada de RateLimiter
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter(BucketStore bucketStore) {
        RateLimitConfig defaultConfig = RateLimitConfig.builder()
                .tokensPerPeriod(defaultLimit)
                .period(Duration.ofSeconds(defaultDurationSeconds))
                .refillStrategy(greedy ? 
                        RateLimitConfig.RefillStrategy.GREEDY : 
                        RateLimitConfig.RefillStrategy.GRADUAL)
                .build();
        
        return RateLimiter.builder()
                .withBucketStore(bucketStore)
                .withKeyGenerator(new SimpleKeyGenerator())
                .withDefaultConfig(defaultConfig)
                .build();
    }
    
    /**
     * Registra o adaptador Spring para interceptar anotações @RateLimit.
     * 
     * @param rateLimiter o limitador de taxa a ser usado
     * @return instância configurada de SpringRateLimitAdapter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "ratelimit.enabled", havingValue = "true", matchIfMissing = true)
    public SpringRateLimitAdapter springRateLimitAdapter(RateLimiter rateLimiter) {
        return new SpringRateLimitAdapter(rateLimiter);
    }
    
    /**
     * Configuração para armazenamento em memória (padrão).
     */
    @Configuration
    @ConditionalOnProperty(name = "ratelimit.storage", havingValue = "inmemory", matchIfMissing = true)
    public static class InMemoryConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        public BucketStore bucketStore() {
            return new InMemoryBucketStore();
        }
    }
    
    /**
     * Configuração para armazenamento Redis.
     */
    @Configuration
    @ConditionalOnProperty(name = "ratelimit.storage", havingValue = "redis")
    @ConditionalOnClass(RedisClient.class)
    public static class RedisConfiguration {
        
        @Value("${ratelimit.redis.host:localhost}")
        private String host;
        
        @Value("${ratelimit.redis.port:6379}")
        private int port;
        
        @Value("${ratelimit.redis.database:0}")
        private int database;
        
        @Value("${ratelimit.redis.password:}")
        private String password;
        
        @Value("${ratelimit.redis.timeout:2000}")
        private long timeoutMillis;
        
        @Value("${ratelimit.redis.key-prefix:ratelimit:}")
        private String keyPrefix;
        
        @Value("${ratelimit.redis.cache-expiry:60000}")
        private long cacheExpiryMillis;
        
        /**
         * Configura o cliente Lettuce para Redis.
         * 
         * @return instância configurada de RedisClient
         */
        @Bean(destroyMethod = "shutdown")
        @ConditionalOnMissingBean
        public RedisClient redisClient() {
            RedisURI.Builder builder = RedisURI.builder()
                    .withHost(host)
                    .withPort(port)
                    .withDatabase(database)
                    .withTimeout(Duration.ofMillis(timeoutMillis));
            
            if (password != null && !password.isEmpty()) {
                builder.withPassword(password.toCharArray());
            }
            
            return RedisClient.create(builder.build());
        }
        
        /**
         * Configura a conexão Redis com codec para byte[].
         * 
         * @param redisClient cliente Redis
         * @return conexão configurada
         */
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean
        public StatefulRedisConnection<String, byte[]> redisConnection(RedisClient redisClient) {
            return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        }
        
        /**
         * Configura o cliente Redis para uso com a SDK.
         * 
         * @param redisConnection conexão Redis
         * @return cliente Redis para a SDK
         */
        @Bean
        @ConditionalOnMissingBean(LettuceRedisClient.class)
        public LettuceRedisClient lettuceRedisClient(StatefulRedisConnection<String, byte[]> redisConnection) {
            return LettuceRedisClient.builder()
                    .withConnection(redisConnection)
                    .build();
        }
        
        /**
         * Configura o armazenamento Redis para buckets.
         * 
         * @param redisClient cliente Redis
         * @return armazenamento Redis para buckets
         */
        @Bean
        @ConditionalOnMissingBean
        public BucketStore bucketStore(LettuceRedisClient redisClient) {
            return new RedisBucketStore(redisClient, keyPrefix, cacheExpiryMillis);
        }
    }
} 