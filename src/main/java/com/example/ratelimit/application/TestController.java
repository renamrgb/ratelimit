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
            limit = 2,
            timeUnit = RateLimit.TimeUnit.MINUTES,
            retry = @Retry(
                    maxAttempts = 5,
                    include = {RuntimeException.class},
                    backoff = @BackoffRetry(
                            delay = 1000,
                            maxDelay = 2000,
                            multiplier = 2
                    )
            )
    )
    @GetMapping
    public ResponseEntity<String> test() {
        throw new RuntimeException();
//        return ResponseEntity.ok("Test");
    }
}
