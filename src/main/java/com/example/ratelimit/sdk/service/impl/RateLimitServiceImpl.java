package com.example.ratelimit.sdk.service.impl;

import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.service.RateLimitService;
import com.example.ratelimit.sdk.service.factory.BucketFactory;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@Service
public class RateLimitServiceImpl implements RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitServiceImpl.class);
    private static final String RATE_LIMITER_PREFIX = "rate_limiter:";
    
    private final BucketFactory bucketFactory;
    private final Counter rateLimitExceededCounter;
    private final Counter rateLimitSuccessCounter;
    private final Timer rateLimitResponseTime;
    
    @Value("${rate-limit.enable-metrics:false}")
    private boolean enableMetrics;
    
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired
    public RateLimitServiceImpl(BucketFactory bucketFactory, 
                               Counter rateLimitExceededCounter,
                               Counter rateLimitSuccessCounter,
                               Timer rateLimitResponseTime) {
        this.bucketFactory = bucketFactory;
        this.rateLimitExceededCounter = rateLimitExceededCounter;
        this.rateLimitSuccessCounter = rateLimitSuccessCounter;
        this.rateLimitResponseTime = rateLimitResponseTime;
    }

    @Override
    public String generateKey(String className, String methodName, String ip, String userId) {
        StringBuilder keyBuilder = new StringBuilder(className).append(".").append(methodName);
        
        if (ip != null && !ip.isEmpty()) {
            keyBuilder.append(".").append(ip);
        }
        
        if (userId != null && !userId.isEmpty()) {
            keyBuilder.append(".").append(userId);
        }
        
        return keyBuilder.toString();
    }

    @Override
    public Bucket getBucket(String key, RateLimit rateLimit) {
        return bucketFactory.createBucket(key, rateLimit);
    }

    @Override
    public boolean consumeToken(String key, RateLimit rateLimit, long tokens) {
        Timer.Sample sample = Timer.start();
        try {
            Bucket bucket = getBucket(key, rateLimit);
            boolean consumed = bucket.tryConsume(tokens);
            if (consumed) {
                rateLimitSuccessCounter.increment();
            } else {
                rateLimitExceededCounter.increment();
                logger.warn("Rate limit exceeded for key: {}", key);
            }
            return consumed;
        } finally {
            sample.stop(rateLimitResponseTime);
        }
    }

    @Override
    public CompletableFuture<Boolean> consumeTokenAsync(String key, RateLimit rateLimit, long tokens) {
        Timer.Sample sample = Timer.start();
        
        Bucket bucket = getBucket(key, rateLimit);
        // Usando tryConsume para operação síncrona mas envolto em CompletableFuture para manter API assíncrona
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> bucket.tryConsume(tokens));
        
        future.thenAccept(consumed -> {
            if (consumed) {
                rateLimitSuccessCounter.increment();
            } else {
                rateLimitExceededCounter.increment();
                logger.warn("Rate limit exceeded for key: {}", key);
            }
            sample.stop(rateLimitResponseTime);
        });
        
        return future;
    }
} 