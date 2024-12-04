package com.example.ratelimit.sdk.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface BackoffRetry {

    long delay() default 1000L;

    long maxDelay() default 0L;

    double multiplier() default 1.0;
}
