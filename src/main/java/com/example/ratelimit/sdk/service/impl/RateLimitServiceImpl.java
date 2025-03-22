package com.example.ratelimit.sdk.service.impl;

import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.service.RateLimitService;
import com.example.ratelimit.sdk.service.factory.BucketFactory;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;

@Service
public class RateLimitServiceImpl implements RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitServiceImpl.class);
    private static final String RATE_LIMITER_PREFIX = "rate_limiter:";
    
    private final BucketFactory bucketFactory;
    
    @Value("${rate-limit.enable-metrics:false}")
    private boolean enableMetrics;
    
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired
    public RateLimitServiceImpl(BucketFactory bucketFactory) {
        this.bucketFactory = bucketFactory;
    }

    @Override
    public String generateKey(String userKey, String methodName) {
        return RATE_LIMITER_PREFIX + userKey + ":" + methodName;
    }

    @Override
    public Bucket getBucket(String key, RateLimit rateLimit) {
        return bucketFactory.createBucket(key, rateLimit);
    }
    
    @Override
    public void consumeToken(Bucket bucket, String key) {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (enableMetrics && meterRegistry != null) {
            meterRegistry.gauge("rate_limit.remaining_tokens", 
                    Arrays.asList(Tag.of("key", key)), 
                    probe.getRemainingTokens());
        }
        
        if (!probe.isConsumed()) {
            long waitForRefillMillis = probe.getNanosToWaitForRefill() / 1_000_000;
            logger.warn("[RateLimit] Key: {}, Sem tokens disponíveis. Aguardando aproximadamente {} ms", key, waitForRefillMillis);
            
            try {
                // Usar o modo de bloqueio para aguardar até que um token esteja disponível
                bucket.asBlocking().consume(1);
                logger.info("[RateLimit] Key: {}, Token adquirido após aguardar", key);
                
                if (enableMetrics && meterRegistry != null) {
                    meterRegistry.timer("rate_limit.wait_time", 
                            Arrays.asList(Tag.of("key", key)))
                            .record(Duration.ofMillis(waitForRefillMillis));
                }
            } catch (InterruptedException e) {
                logger.error("[RateLimit] Thread interrompida enquanto aguardava token", e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrompido enquanto aguardava token disponível", e);
            }
        }
    }
    
    @Override
    public boolean consumeTokenAsync(Bucket bucket, String key) throws InterruptedException {
        try {
            // Em vez de tentar consumir, usamos o modo de bloqueio para garantir
            // que a operação assíncrona aguarde até que um token esteja disponível
            bucket.asBlocking().consume(1);
            return true;
        } catch (InterruptedException e) {
            logger.error("[RateLimit] Thread interrompida enquanto aguardava token assíncrono", e);
            Thread.currentThread().interrupt();
            throw e;
        }
    }
} 