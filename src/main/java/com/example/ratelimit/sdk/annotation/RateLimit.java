package com.example.ratelimit.sdk.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface RateLimit {
    String key();

    int limit() default 10;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    Retry retry() default @Retry(maxAttempts = 0);

    enum TimeUnit {
        SECONDS(1_000_000_000),
        MINUTES(60L * 1_000_000_000),
        HOURS(3600L * 1_000_000_000);

        private final long timeInNanos;

        TimeUnit(long timeInNanos) {
            this.timeInNanos = timeInNanos;
        }

        public long getTimeInNanos() {
            return timeInNanos;
        }
    }
}
