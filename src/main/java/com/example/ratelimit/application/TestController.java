package com.example.ratelimit.application;

import com.example.ratelimit.sdk.annotation.BackoffRetry;
import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.annotation.Retry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

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
    @GetMapping
    public ResponseEntity<String> test() {
        throw new IllegalArgumentException("Teste de retry");
//        return ResponseEntity.ok("Test");
    }

    @RateLimit(
            key = "test-normal",
            limit = 10,
            timeUnit = RateLimit.TimeUnit.SECONDS
    )
    @GetMapping("/normal")
    public ResponseEntity<String> normalTest() {
        return ResponseEntity.ok("Test Normal");
    }
    
    @RateLimit(
            key = "test-async",
            limit = 3,
            timeUnit = RateLimit.TimeUnit.SECONDS,
            async = true
    )
    @GetMapping("/async")
    public ResponseEntity<String> asyncTest() {
        return ResponseEntity.ok("Teste com Rate Limit Assíncrono");
    }
    
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
    @GetMapping("/async-retry")
    public ResponseEntity<String> asyncRetryTest() {
        double random = Math.random();
        if (random < 0.7) {
            throw new RuntimeException("Erro simulado para testar retry assíncrono");
        }
        return ResponseEntity.ok("Teste com Rate Limit Assíncrono e Retry");
    }

    public ResponseEntity<String> fallback(Throwable throwable) {
        return ResponseEntity.ok(String.format("Fallback method " + throwable.getMessage()));
    }
}
