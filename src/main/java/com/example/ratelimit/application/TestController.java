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

    public ResponseEntity<String> fallback(Throwable throwable) {
        return ResponseEntity.ok(String.format("Fallback method " + throwable.getMessage()));
    }
}
