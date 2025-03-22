package com.ratelimit.core;

import com.ratelimit.adapters.inmemory.InMemoryBucketStore;
import com.ratelimit.core.model.RateLimitConfig;
import com.ratelimit.spi.KeyGenerator;
import com.ratelimit.util.SimpleKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private static final String RESOURCE_ID = "test-resource";
    private RateLimiter rateLimiter;
    private InMemoryBucketStore bucketStore;

    @BeforeEach
    void setUp() {
        bucketStore = new InMemoryBucketStore();
        
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(5)
                .period(Duration.ofSeconds(1))
                .build();
        
        rateLimiter = RateLimiter.builder()
                .withBucketStore(bucketStore)
                .withKeyGenerator(new SimpleKeyGenerator())
                .withDefaultConfig(config)
                .build();
    }

    @Test
    void testTryAcquire_ShouldAcquireTokensWithinLimit() {
        // Teste para adquirir tokens dentro do limite
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire(RESOURCE_ID), "Deveria adquirir token " + (i + 1));
        }
    }

    @Test
    void testTryAcquire_ShouldRejectWhenExceedingLimit() {
        // Consome todos os tokens disponíveis
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire(RESOURCE_ID));
        }
        
        // Tentar consumir mais deve falhar
        assertFalse(rateLimiter.tryAcquire(RESOURCE_ID), "Não deveria permitir exceder o limite");
    }

    @Test
    void testTryAcquire_WithMultipleTokens() {
        // Consome 3 tokens de uma vez
        assertTrue(rateLimiter.tryAcquire(RESOURCE_ID, 3), "Deveria adquirir 3 tokens");
        
        // Consome mais 2 tokens (chegando ao limite)
        assertTrue(rateLimiter.tryAcquire(RESOURCE_ID, 2), "Deveria adquirir 2 tokens");
        
        // Tentar consumir mais 1 deve falhar
        assertFalse(rateLimiter.tryAcquire(RESOURCE_ID, 1), "Não deveria permitir exceder o limite");
    }

    @Test
    void testTryAcquireAsync_ShouldAcquireTokensWithinLimit() throws Exception {
        // Teste para adquirir tokens assincronamente
        CompletableFuture<Boolean>[] futures = new CompletableFuture[5];
        
        for (int i = 0; i < 5; i++) {
            futures[i] = rateLimiter.tryAcquireAsync(RESOURCE_ID);
        }
        
        // Verifica que todos conseguiram tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(futures[i].get(), "Deveria adquirir token assíncrono " + (i + 1));
        }
        
        // O próximo deve falhar
        assertFalse(rateLimiter.tryAcquireAsync(RESOURCE_ID).get(), 
                "Não deveria permitir exceder o limite assincronamente");
    }

    @Test
    void testTryAcquireAsync_WithMultipleTokens() throws Exception {
        // Consome 3 tokens de uma vez assincronamente
        assertTrue(rateLimiter.tryAcquireAsync(RESOURCE_ID, 3).get(), 
                "Deveria adquirir 3 tokens assincronamente");
        
        // Consome mais 2 tokens (chegando ao limite)
        assertTrue(rateLimiter.tryAcquireAsync(RESOURCE_ID, 2).get(), 
                "Deveria adquirir 2 tokens assincronamente");
        
        // Tentar consumir mais 1 deve falhar
        assertFalse(rateLimiter.tryAcquireAsync(RESOURCE_ID, 1).get(), 
                "Não deveria permitir exceder o limite assincronamente");
    }

    @Test
    void testTryAcquire_WithIllegalArguments() {
        // Teste com argumentos inválidos
        assertThrows(NullPointerException.class, () -> rateLimiter.tryAcquire(null),
                "Deveria lançar NullPointerException para resourceId nulo");
        
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.tryAcquire(RESOURCE_ID, 0),
                "Deveria lançar IllegalArgumentException para tokens <= 0");
        
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.tryAcquire(RESOURCE_ID, -1),
                "Deveria lançar IllegalArgumentException para tokens <= 0");
    }

    @Test
    void testTryAcquireAsync_WithIllegalArguments() {
        // Teste com argumentos inválidos
        assertThrows(NullPointerException.class, () -> rateLimiter.tryAcquireAsync(null),
                "Deveria lançar NullPointerException para resourceId nulo");
        
        CompletableFuture<Boolean> future = rateLimiter.tryAcquireAsync(RESOURCE_ID, 0);
        assertThrows(Exception.class, () -> future.get(),
                "Deveria lançar exceção para tokens <= 0");
    }

    @Test
    void testDifferentResources_ShouldHaveSeparateLimits() {
        // Recurso 1 consome todos os seus tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire(RESOURCE_ID + "-1"));
        }
        
        // Recurso 1 não deve ter mais tokens
        assertFalse(rateLimiter.tryAcquire(RESOURCE_ID + "-1"));
        
        // Recurso 2 deve ter seus próprios tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire(RESOURCE_ID + "-2"),
                    "Recurso diferente deveria ter seu próprio limite");
        }
    }
    
    @Test
    void testBuilder_WithIllegalArguments() {
        // Teste de builder com argumentos inválidos
        assertThrows(NullPointerException.class, () -> 
                RateLimiter.builder().withBucketStore(null),
                "Deveria lançar NullPointerException para bucketStore nulo");
        
        assertThrows(NullPointerException.class, () -> 
                RateLimiter.builder().withKeyGenerator(null),
                "Deveria lançar NullPointerException para keyGenerator nulo");
        
        assertThrows(NullPointerException.class, () -> 
                RateLimiter.builder().withDefaultConfig(null),
                "Deveria lançar NullPointerException para defaultConfig nulo");
    }
} 