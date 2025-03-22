package com.example.ratelimit.sdk.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    @ConditionalOnProperty(value = "rate-limit.enable-metrics", havingValue = "true")
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
} 