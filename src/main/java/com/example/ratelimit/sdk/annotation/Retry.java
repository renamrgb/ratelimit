package com.example.ratelimit.sdk.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Retry {

    int maxAttempts() default 3;

    Class<? extends Throwable>[] include() default {};

    Class<? extends Throwable>[] exclude() default {};

    BackoffRetry backoff() default @BackoffRetry;
}
