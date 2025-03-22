package com.example.ratelimit.sdk.exception;

/**
 * Exceção lançada quando uma taxa limite é excedida
 */
public class RateLimitExceededException extends RuntimeException {
    
    public RateLimitExceededException(String message) {
        super(message);
    }
    
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
} 