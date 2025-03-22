package com.example.ratelimit.service.impl;

import com.example.ratelimit.sdk.annotation.BackoffRetry;
import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.annotation.Retry;
import com.example.ratelimit.service.TestService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class TestServiceImpl implements TestService {

    @Override
    @RateLimit(
            key = "test",
            limit = 5,
            timeUnit = RateLimit.TimeUnit.MINUTES,
            overdraft = 2,
            greedyRefill = true,
            retry = @Retry(
                    maxAttempts = 4,
                    include = {IllegalArgumentException.class},
                    exclude = {IllegalStateException.class},
                    description = "Retry para o endpoint de teste, com backoff exponencial",
                    fallbackMethod = "fallback",
                    backoff = @BackoffRetry(
                            delay = 1000,
                            multiplier = 2,
                            maxDelay = 10000
                    )
            )
    )
    public ResponseEntity<String> test() {
        throw new IllegalArgumentException("Teste de retry");
    }

    @Override
    @RateLimit(
            key = "test-normal",
            limit = 10,
            timeUnit = RateLimit.TimeUnit.SECONDS
    )
    public ResponseEntity<String> normalTest() {
        return ResponseEntity.ok("Test Normal");
    }
    
    @Override
    @RateLimit(
            key = "test-async",
            limit = 3,
            timeUnit = RateLimit.TimeUnit.SECONDS,
            async = true
    )
    public ResponseEntity<String> asyncTest() {
        return ResponseEntity.ok("Teste com Rate Limit Assíncrono");
    }
    
    @Override
    @RateLimit(
            key = "test-async-retry",
            limit = 5,
            timeUnit = RateLimit.TimeUnit.MINUTES,
            async = true,
            retry = @Retry(
                    maxAttempts = 3,
                    include = {RuntimeException.class},
                    fallbackMethod = "fallback",
                    backoff = @BackoffRetry(
                            delay = 500,
                            multiplier = 2
                    )
            )
    )
    public ResponseEntity<String> asyncRetryTest() {
        double random = Math.random();
        if (random < 0.7) {
            throw new RuntimeException("Erro simulado para testar retry assíncrono");
        }
        return ResponseEntity.ok("Teste com Rate Limit Assíncrono e Retry");
    }

    @Override
    public ResponseEntity<String> fallback(Throwable throwable) {
        return ResponseEntity.ok(String.format("Fallback method " + throwable.getMessage()));
    }
} 