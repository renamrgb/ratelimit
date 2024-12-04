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
            retry = @Retry(
                    maxAttempts = 4,
                    include = {IllegalArgumentException.class},
                    exclude = {IllegalStateException.class},
                    backoff = @BackoffRetry(
                            delay = 1000,
                            multiplier = 2,
                            maxDelay = 10000
                    )
            )
    )
    @GetMapping
    public ResponseEntity<String> test() {
        throw new IllegalArgumentException();
//        return ResponseEntity.ok("Test");
    }

    public ResponseEntity<String> fallback(Throwable throwable) {
        return ResponseEntity.ok(String.format("Fallback method " + throwable.getMessage()));
    }
}
