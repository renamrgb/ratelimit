package com.example.ratelimit.sdk.aop;

import com.example.ratelimit.sdk.annotation.BackoffRetry;
import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.annotation.Retry;
import com.example.ratelimit.sdk.exception.RateLimitExceededException;
import com.example.ratelimit.sdk.service.RateLimitService;
import io.github.bucket4j.Bucket;
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

    private final RateLimitService rateLimitService;
    
    @Value("${rate-limit.enable-metrics:false}")
    private boolean enableMetrics;
    
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired
    public RateLimitAspect(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // Obter informações do método
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getSignature().getDeclaringTypeName();
        
        // Gerar chave usando informações básicas
        String key = rateLimitService.generateKey(className, methodName, null, null);
        logger.debug("Aplicando rate limit para chave: {}", key);
        
        // Decidir qual método de execução usar com base nas configurações
        if (rateLimit.async()) {
            return executeAsync(joinPoint, rateLimit, key);
        } else if (rateLimit.retry().maxAttempts() > 1) {
            return executeWithRetry(joinPoint, rateLimit, key);
        } else {
            return execute(joinPoint, rateLimit, key);
        }
    }
    
    private Object execute(ProceedingJoinPoint joinPoint, RateLimit rateLimit, String key) throws Throwable {
        boolean allowed = rateLimitService.consumeToken(key, rateLimit, 1L);
        
        if (!allowed) {
            throw new RateLimitExceededException("Taxa limite excedida para chave: " + key);
        }
        
        return joinPoint.proceed();
    }

    private Object executeAsync(ProceedingJoinPoint joinPoint, RateLimit rateLimit, String key) throws Throwable {
        CompletableFuture<Boolean> future = rateLimitService.consumeTokenAsync(key, rateLimit, 1L);
        
        return future.thenApplyAsync(allowed -> {
            try {
                if (!allowed) {
                    throw new RateLimitExceededException("Taxa limite excedida para chave: " + key);
                }
                return joinPoint.proceed();
            } catch (Throwable e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
        }).get();
    }
    
    private Object executeWithRetry(ProceedingJoinPoint joinPoint, RateLimit rateLimit, String key) throws Throwable {
        int retries = 0;
        int maxRetries = rateLimit.retry().maxAttempts();
        
        while (true) {
            boolean allowed = rateLimitService.consumeToken(key, rateLimit, 1L);
            
            if (allowed) {
                return joinPoint.proceed();
            }
            
            if (++retries >= maxRetries) {
                throw new RateLimitExceededException("Taxa limite excedida após " + retries + " tentativas para chave: " + key);
            }
            
            logger.debug("Taxa limite excedida para chave: {}. Tentativa: {}", key, retries);
            
            // Backoff exponencial com jitter
            long delay = (long) (Math.pow(2, retries) * rateLimit.retry().backoff().delay() * (0.5 + Math.random() * 0.5));
            Thread.sleep(delay);
        }
    }

    private String getClientIp() {
        // Implemente a lógica para obter o IP do cliente
        return "127.0.0.1";
    }

    private String getUserId() {
        // Implemente a lógica para obter o ID do usuário
        return "user123";
    }
}
