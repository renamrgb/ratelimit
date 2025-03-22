package com.example.ratelimit.sdk.aop;

import com.example.ratelimit.sdk.annotation.BackoffRetry;
import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.annotation.Retry;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitAspect.class);
    private static final String RATE_LIMITER_PREFIX = "rate_limiter:";
    
    private final LettuceBasedProxyManager<String> proxyManager;
    private final Map<String, Bucket> localBucketCache = new ConcurrentHashMap<>();
    
    @Value("${rate-limit.enable-metrics:false}")
    private boolean enableMetrics;
    
    @Value("${rate-limit.cache-expiry-seconds:300}")
    private long cacheExpirySeconds;
    
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    public RateLimitAspect(StatefulRedisConnection<String, byte[]> redisConnection) {
        this.proxyManager = LettuceBasedProxyManager
                .builderFor(redisConnection)
                .withExpirationStrategy(
                    ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        Duration.ofSeconds(cacheExpirySeconds)))
                .build();
    }

    @Around("@annotation(rateLimit)")
    public Object rateLimit(final ProceedingJoinPoint joinPoint, final RateLimit rateLimit) throws Throwable {
        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        final Method method = signature.getMethod();
        final String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        
        String key = generateKey(rateLimit.key(), methodName);
        Retry retry = rateLimit.retry();

        Timer.Sample sample = null;
        if (enableMetrics && meterRegistry != null) {
            sample = Timer.start(meterRegistry);
        }

        try {
            if (rateLimit.async()) {
                return executeAsync(joinPoint, key, rateLimit, retry);
            } else {
                if (retry.maxAttempts() > 1) {
                    return executeWithRetry(joinPoint, retry, key, rateLimit);
                } else {
                    return execute(joinPoint, key, rateLimit);
                }
            }
        } finally {
            if (sample != null) {
                sample.stop(meterRegistry.timer("rate_limit.execution", 
                        Arrays.asList(
                            Tag.of("method", methodName),
                            Tag.of("key", rateLimit.key()),
                            Tag.of("async", String.valueOf(rateLimit.async()))
                        )));
            }
        }
    }

    private String generateKey(String userKey, String methodName) {
        return RATE_LIMITER_PREFIX + userKey + ":" + methodName;
    }

    private Object execute(final ProceedingJoinPoint joinPoint, final String key, final RateLimit rateLimit) throws Throwable {
        Bucket bucket = resolveBucket(key, rateLimit);
        consumeToken(bucket, key);
        return joinPoint.proceed();
    }
    
    private Object executeAsync(final ProceedingJoinPoint joinPoint, final String key, 
                               final RateLimit rateLimit, final Retry retry) throws Throwable {
        if (retry.maxAttempts() > 1) {
            final RetryTemplate retryTemplate = createRetryTemplate(retry);
            return retryTemplate.execute(context -> {
                try {
                    return executeAsyncInternal(joinPoint, key, rateLimit);
                } catch (Throwable ex) {
                    if (shouldRetry(ex, retry)) {
                        logger.warn("[Retry] Retrying after exception: {}, Attempt: {}", ex.getClass().getSimpleName(),
                                context.getRetryCount() + 1);

                        if (enableMetrics && meterRegistry != null) {
                            meterRegistry.counter("rate_limit.retry.count", 
                                    Arrays.asList(
                                        Tag.of("key", key),
                                        Tag.of("exception", ex.getClass().getSimpleName())
                                    )).increment();
                        }

                        throw ex;
                    }
                    throw ex;
                }
            }, context -> {
                Throwable ex = context.getLastThrowable();
                if (!retry.fallbackMethod().isEmpty()) {
                    logger.info("[Fallback] Max retries reached. Invoking fallback: {}", retry.fallbackMethod());
                    return invokeFallback(joinPoint, retry.fallbackMethod(), ex);
                }
                throw new RuntimeException("Rate limit exceeded with retries exhausted", ex);
            });
        } else {
            return executeAsyncInternal(joinPoint, key, rateLimit);
        }
    }
    
    private Object executeAsyncInternal(final ProceedingJoinPoint joinPoint, 
                                      final String key, final RateLimit rateLimit) throws Throwable {
        BucketConfiguration bucketConfig = createBucketConfiguration(rateLimit);
        try {
            Bucket bucket = proxyManager.builder().build(key, () -> bucketConfig);
            
            CompletableFuture<Boolean> consumptionFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    // Em vez de tentar consumir, usamos o modo de bloqueio para garantir
                    // que a operação assíncrona aguarde até que um token esteja disponível
                    bucket.asBlocking().consume(1);
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            });
            
            Boolean consumed = consumptionFuture.get();
            if (!consumed) {
                throw new RuntimeException("Interrompido enquanto aguardava token disponível para: " + key);
            }
            
            return joinPoint.proceed();
        } catch (ExecutionException e) {
            throw new RuntimeException("Falha ao consumir token de forma assíncrona", e.getCause());
        }
    }

    private Object executeWithRetry(final ProceedingJoinPoint joinPoint, final Retry retry, 
                                   final String key, final RateLimit rateLimit) throws Throwable {
        final RetryTemplate retryTemplate = createRetryTemplate(retry);
        return retryTemplate.execute(context -> {
            try {
                return execute(joinPoint, key, rateLimit);
            } catch (Throwable ex) {
                if (shouldRetry(ex, retry)) {
                    logger.warn("[Retry] Retrying after exception: {}, Attempt: {}", ex.getClass().getSimpleName(),
                            context.getRetryCount() + 1);

                    if (enableMetrics && meterRegistry != null) {
                        meterRegistry.counter("rate_limit.retry.count", 
                                Arrays.asList(
                                    Tag.of("key", key),
                                    Tag.of("exception", ex.getClass().getSimpleName())
                                )).increment();
                    }

                    throw ex;
                }
                throw ex;
            }
        }, context -> {
            Throwable ex = context.getLastThrowable();
            if (!retry.fallbackMethod().isEmpty()) {
                logger.info("[Fallback] Max retries reached. Invoking fallback: {}", retry.fallbackMethod());
                return invokeFallback(joinPoint, retry.fallbackMethod(), ex);
            }
            throw new RuntimeException("Rate limit exceeded with retries exhausted", ex);
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
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
            paramTypes[args.length] = Throwable.class;

            final Method method = findFallbackMethod(targetClass, fallbackMethod, paramTypes);
            logger.info("[Fallback] Invoking fallback method: {}", fallbackMethod);
            
            if (enableMetrics && meterRegistry != null) {
                meterRegistry.counter("rate_limit.fallback.count").increment();
            }
            
            return method.invoke(target, extendedArgs);
        } catch (Exception ex) {
            logger.error("[Fallback] Error invoking fallback method: {}", fallbackMethod, ex);
            throw new RuntimeException("Error invoking fallback method: " + fallbackMethod, ex);
        }
    }

    private Method findFallbackMethod(Class<?> targetClass, String fallbackMethodName, Class<?>[] paramTypes) throws NoSuchMethodException {
        try {
            return targetClass.getMethod(fallbackMethodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // Tentar encontrar método com tipos compatíveis
            for (Method method : targetClass.getMethods()) {
                if (method.getName().equals(fallbackMethodName) && method.getParameterCount() == paramTypes.length) {
                    boolean match = true;
                    Class<?>[] methodParams = method.getParameterTypes();
                    for (int i = 0; i < methodParams.length; i++) {
                        if (!methodParams[i].isAssignableFrom(paramTypes[i])) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        return method;
                    }
                }
            }
            throw new NoSuchMethodException("No compatible fallback method found: " + fallbackMethodName);
        }
    }

    private Bucket resolveBucket(final String key, final RateLimit rateLimit) {
        // Tenta obter do cache local primeiro para reduzir overhead de rede
        return localBucketCache.computeIfAbsent(key, k -> {
            BucketConfiguration bucketConfig = createBucketConfiguration(rateLimit);
            return proxyManager.builder().build(k, () -> bucketConfig);
        });
    }
    
    private BucketConfiguration createBucketConfiguration(RateLimit rateLimit) {
        return BucketConfiguration.builder()
                .addLimit(createBandwidth(rateLimit))
                .build();
    }
    
    private Bandwidth createBandwidth(RateLimit rateLimit) {
        long nanosInTimeUnit = rateLimit.timeUnit().getTimeInNanos();
        
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
    
    private void consumeToken(Bucket bucket, String key) {
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
    
    private boolean shouldRetry(Throwable ex, Retry retry) {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = createRetryableExceptions(retry);
        
        for (Map.Entry<Class<? extends Throwable>, Boolean> entry : retryableExceptions.entrySet()) {
            if (entry.getKey().isAssignableFrom(ex.getClass())) {
                return entry.getValue();
            }
        }
        
        return false;
    }
}
