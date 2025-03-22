package com.example.ratelimit.sdk.configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    @Autowired
    public MetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public Counter rateLimitExceededCounter() {
        return Counter.builder("ratelimit.exceeded")
                .description("Count of rate limit exceeded events")
                .register(meterRegistry);
    }

    @Bean
    public Counter rateLimitSuccessCounter() {
        return Counter.builder("ratelimit.success")
                .description("Count of successful rate-limited requests")
                .register(meterRegistry);
    }

    @Bean
    public Timer rateLimitResponseTime() {
        return Timer.builder("ratelimit.response.time")
                .description("Response time for rate-limited operations")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(meterRegistry);
    }
} 