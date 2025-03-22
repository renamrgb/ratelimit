package com.example.ratelimit.sdk.service.factory.impl;

import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.service.factory.BucketFactory;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação em memória da fábrica de buckets para testes e desenvolvimento local
 */
@Component
@Profile({"test", "dev"})
public class InMemoryBucketFactory implements BucketFactory {

    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();
    
    @Override
    public Bucket createBucket(String key, RateLimit rateLimit) {
        return localBuckets.computeIfAbsent(key, k -> {
            LocalBucketBuilder builder = Bucket.builder();
            builder.addLimit(createBandwidth(rateLimit));
            return builder.build();
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
    
    /**
     * Limpa os buckets locais, útil para testes
     */
    public void clearBuckets() {
        localBuckets.clear();
    }
} 