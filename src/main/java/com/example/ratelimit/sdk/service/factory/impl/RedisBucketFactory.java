package com.example.ratelimit.sdk.service.factory.impl;

import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.service.factory.BucketFactory;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Primary
public class RedisBucketFactory implements BucketFactory {

    private final LettuceBasedProxyManager<String> proxyManager;
    private final Map<String, Bucket> localBucketCache = new ConcurrentHashMap<>();
    
    @Value("${rate-limit.cache-expiry-seconds:300}")
    private long cacheExpirySeconds;

    @Autowired
    public RedisBucketFactory(StatefulRedisConnection<String, byte[]> redisConnection) {
        Duration expiry = Duration.ofSeconds(cacheExpirySeconds);
        this.proxyManager = Bucket4jLettuce.casBasedBuilder(redisConnection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(expiry))
                .build();
    }

    @Override
    public Bucket createBucket(String key, RateLimit rateLimit) {
        // Tenta obter do cache local primeiro para reduzir overhead de rede
        return localBucketCache.computeIfAbsent(key, k -> {
            BucketConfiguration bucketConfig = createBucketConfiguration(rateLimit);
            return proxyManager.getProxy(k, () -> bucketConfig);
        });
    }

    @Override
    public BucketConfiguration createBucketConfiguration(RateLimit rateLimit) {
        return BucketConfiguration.builder()
                .addLimit(createBandwidth(rateLimit))
                .build();
    }

    @Override
    public Bandwidth createBandwidth(RateLimit rateLimit) {
        long nanosInTimeUnit = getTimeInNanos(rateLimit.timeUnit());
        
        Bandwidth bandwidth;
        if (rateLimit.greedyRefill()) {
            bandwidth = Bandwidth.builder()
                .capacity(rateLimit.limit() + rateLimit.overdraft())
                .refillGreedy(rateLimit.limit(), Duration.ofNanos(nanosInTimeUnit))
                .build();
        } else {
            bandwidth = Bandwidth.builder()
                .capacity(rateLimit.limit() + rateLimit.overdraft())
                .refillIntervally(rateLimit.limit(), Duration.ofNanos(nanosInTimeUnit))
                .build();
        }
        
        return bandwidth;
    }
    
    @Override
    public long getTimeInNanos(RateLimit.TimeUnit timeUnit) {
        return timeUnit.getTimeInNanos();
    }
} 