package com.example.ratelimit.application;

import com.example.ratelimit.sdk.annotation.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @RateLimit(
            key = "test",
            limit = 30,
            timeUnit = RateLimit.TimeUnit.MINUTES
    )
    @GetMapping
    public ResponseEntity<String> test() {
//        throw new IllegalArgumentException();
        return ResponseEntity.ok("Test");
    }

    public ResponseEntity<String> fallback(Throwable throwable) {
        return ResponseEntity.ok(String.format("Fallback method " + throwable.getMessage()));
    }
}
