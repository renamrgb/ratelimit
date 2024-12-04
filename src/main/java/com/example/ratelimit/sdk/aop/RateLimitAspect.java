package com.example.ratelimit.sdk.aop;

import com.example.ratelimit.sdk.annotation.BackoffRetry;
import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.annotation.Retry;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.jedis.Bucket4jJedis;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.time.Duration.ofSeconds;

@Aspect
@Component
public class RateLimitAspect {

    private final ProxyManager<String> proxyManager;
    private static final Logger logger = LoggerFactory.getLogger(RateLimitAspect.class);

    public RateLimitAspect(JedisPool jedisPool) {
        this.proxyManager = Bucket4jJedis.casBasedBuilder(jedisPool)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(ofSeconds(10)))
                .keyMapper(Mapper.STRING)
                .build();
    }

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = rateLimit.key();
        Bucket bucket = resolveBucket(key, rateLimit.limit(), rateLimit.timeUnit());
        Retry retry = rateLimit.retry();

        if (retry.maxAttempts() > 1) {
            final RetryTemplate retryTemplate = createRetryTemplate(retry);
            return retryTemplate.execute(context -> {
                try {
                    consumeToken(bucket, key);
                    return joinPoint.proceed();
                } catch (Throwable ex) {
                    if (shouldRetry(ex, retry)) {
                        logger.warn("[Retry] Retrying after exception: {}, Attempt: {}", ex.getClass().getSimpleName(),
                                context.getRetryCount() + 1);
                        throw ex;
                    }
                    throw ex;
                }
            });
        } else {
            consumeToken(bucket, key);
            return joinPoint.proceed();
        }
    }

    private Bucket resolveBucket(String key, int limit, RateLimit.TimeUnit timeUnit) {
        final long refillIntervalNanos = timeUnit.getTimeInNanos() / limit;

        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(1)
                .refillIntervally(1, Duration.ofNanos(refillIntervalNanos)) // Configura o intervalo de recarga
                .build();

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(bandwidth)
                .build();
        return proxyManager.getProxy(key, () -> configuration);
    }

    private RetryTemplate createRetryTemplate(Retry retry) {
        RetryTemplate retryTemplate = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(retry.maxAttempts(), createRetryableExceptions(retry));
        retryTemplate.setRetryPolicy(retryPolicy);

        BackoffRetry backoff = retry.backoff();
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(backoff.delay());
        backOffPolicy.setMultiplier(backoff.multiplier() > 0 ? backoff.multiplier() : 1.0);
        backOffPolicy.setMaxInterval(backoff.maxDelay() > 0 ? backoff.maxDelay() : backoff.delay() * 10);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    private Map<Class<? extends Throwable>, Boolean> createRetryableExceptions(Retry retry) {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new ConcurrentHashMap<>();
        for (Class<? extends Throwable> included : retry.include()) {
            retryableExceptions.put(included, true);
        }
        for (Class<? extends Throwable> excluded : retry.exclude()) {
            retryableExceptions.put(excluded, false);
        }
        return retryableExceptions;
    }

    private void consumeToken(Bucket bucket, String key) throws InterruptedException {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long waitForRefillMillis = probe.getNanosToWaitForRefill() / 1_000_000_000;
            logger.warn("[RateLimit] Key: {}, No tokens available. Acquiring token in approximately {} ms", key, waitForRefillMillis);

            bucket.asBlocking().consume(1);
            logger.info("[RateLimit] Key: {}, Token acquired after wait", key);
        }
    }

    private boolean shouldRetry(Throwable ex, Retry retry) {
        for (Class<? extends Throwable> excluded : retry.exclude()) {
            if (excluded.isAssignableFrom(ex.getClass())) {
                return false; // Se a exceção está na lista de exclusão, não faz retry
            }
        }
        if (retry.include().length == 0) {
            return true; // Sem restrição, faz retry para todas as exceções
        }
        for (Class<? extends Throwable> included : retry.include()) {
            if (included.isAssignableFrom(ex.getClass())) {
                return true; // Faz retry se a exceção está na lista de inclusão
            }
        }
        return false; // Não faz retry para exceções fora da lista de inclusão
    }
}
