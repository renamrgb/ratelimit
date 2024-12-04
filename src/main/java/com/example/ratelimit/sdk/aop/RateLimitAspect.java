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

import java.lang.reflect.Method;
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
    public Object rateLimit(final ProceedingJoinPoint joinPoint, final RateLimit rateLimit) throws Throwable {
        String key = rateLimit.key();
        Bucket bucket = resolveBucket(key, rateLimit.limit(), rateLimit.timeUnit());
        Retry retry = rateLimit.retry();

        if (retry.maxAttempts() > 1) {
            return executeWithRetry(joinPoint, retry, bucket, key);
        } else {
            return execute(joinPoint, bucket, key);
        }
    }

    private Object execute(final ProceedingJoinPoint joinPoint, final Bucket bucket, final String key) throws Throwable {
        consumeToken(bucket, key);
        return joinPoint.proceed();
    }

    private Object executeWithRetry(final ProceedingJoinPoint joinPoint, final Retry retry, final Bucket bucket, final String key) throws Throwable {
        final RetryTemplate retryTemplate = createRetryTemplate(retry);
        return retryTemplate.execute(context -> {
            try {
                return execute(joinPoint, bucket, key);
            } catch (Throwable ex) {
                if (shouldRetry(ex, retry)) {
                    logger.warn("[Retry] Retrying after exception: {}, Attempt: {}", ex.getClass().getSimpleName(),
                            context.getRetryCount() + 1);

                    throw ex;
                }
                throw ex;
            }
        }, context -> {
            Throwable ex = context.getLastThrowable();
            if (!retry.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, retry.fallbackMethod(), ex);
            }
            throw new RuntimeException(ex);
        });
    }

    private Object invokeFallback(final ProceedingJoinPoint joinPoint, final String fallbackMethod, final Throwable cause) {
        try {
            final Object target = joinPoint.getTarget();
            final Class<?> targetClass = target.getClass();

            final Object[] args = joinPoint.getArgs();

            final Object[] extendedArgs = new Object[args.length + 1];
            System.arraycopy(args, 0, extendedArgs, 0, args.length);
            extendedArgs[args.length] = cause;

            final Class<?>[] paramTypes = new Class[extendedArgs.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
            }
            paramTypes[args.length] = Throwable.class;

            final Method method = targetClass.getMethod(fallbackMethod, paramTypes);
            logger.info("[Fallback] Invoking fallback method: {}", fallbackMethod);
            return method.invoke(target, extendedArgs);
        } catch (Exception ex) {
            logger.error("[Fallback] Error invoking fallback method: {}", fallbackMethod, ex);
            throw new RuntimeException(ex);
        }
    }

    private Bucket resolveBucket(final String key, final int limit, final RateLimit.TimeUnit timeUnit) {
        final long refillIntervalNanos = timeUnit.getTimeInNanos() / limit;

        final Bandwidth bandwidth = Bandwidth.builder()
                .capacity(1)
                .refillIntervally(1, Duration.ofNanos(refillIntervalNanos)) // Configura o intervalo de recarga
                .build();

        final BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(bandwidth)
                .build();
        return proxyManager.getProxy(key, () -> configuration);
    }

    private RetryTemplate createRetryTemplate(final Retry retry) {
        final RetryTemplate retryTemplate = new RetryTemplate();

        final SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(retry.maxAttempts(), createRetryableExceptions(retry));
        retryTemplate.setRetryPolicy(retryPolicy);

        final BackoffRetry backoff = retry.backoff();

        final ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(backoff.delay());
        backOffPolicy.setMultiplier(backoff.multiplier() > 0 ? backoff.multiplier() : 1.0);
        backOffPolicy.setMaxInterval(backoff.maxDelay() > 0 ? backoff.maxDelay() : backoff.delay() * 10);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    private Map<Class<? extends Throwable>, Boolean> createRetryableExceptions(final Retry retry) {
        final Map<Class<? extends Throwable>, Boolean> retryableExceptions = new ConcurrentHashMap<>();
        for (Class<? extends Throwable> included : retry.include()) {
            retryableExceptions.put(included, true);
        }
        for (Class<? extends Throwable> excluded : retry.exclude()) {
            retryableExceptions.put(excluded, false);
        }
        return retryableExceptions;
    }

    private void consumeToken(final Bucket bucket, final String key) throws InterruptedException {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long waitForRefillMillis = probe.getNanosToWaitForRefill() / 1_000_000;
            logger.warn("[RateLimit] Key: {}, No tokens available. Acquiring token in approximately {} ms", key, waitForRefillMillis);

            bucket.asBlocking().consume(1);
            logger.info("[RateLimit] Key: {}, Token acquired after wait", key);
        }
    }

    private boolean shouldRetry(final Throwable ex, final Retry retry) {
        for (Class<? extends Throwable> excluded : retry.exclude()) {
            if (excluded.isAssignableFrom(ex.getClass())) {
                return false;
            }
        }
        for (Class<? extends Throwable> included : retry.include()) {
            if (included.isAssignableFrom(ex.getClass())) {
                return true;
            }
        }
        return false;
    }
}
