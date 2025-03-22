package com.ratelimit.core.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BucketTest {

    @Test
    void testBucketCreation() {
        // Configuração básica de bucket
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(10)
                .period(Duration.ofSeconds(1))
                .build();
        
        Bucket bucket = new Bucket(config);
        
        assertEquals(10, bucket.getAvailableTokens(), "O bucket deve iniciar com 10 tokens");
        assertEquals(config, bucket.getConfig(), "A configuração do bucket deve ser a mesma informada");
        assertNotNull(bucket.getLastRefillTimestamp(), "O timestamp de última recarga não deve ser nulo");
    }

    @Test
    void testTryConsume_ShouldConsumeTokensWithinLimit() {
        // Configuração básica de bucket
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(5)
                .period(Duration.ofSeconds(1))
                .build();
        
        Bucket bucket = new Bucket(config);
        
        // Consome um token de cada vez
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(), "Deveria consumir o token " + (i + 1));
        }
        
        // Não deve ter mais tokens
        assertFalse(bucket.tryConsume(), "Não deveria ter mais tokens disponíveis");
        assertEquals(0, bucket.getAvailableTokens(), "Deveria ter 0 tokens disponíveis");
    }

    @Test
    void testTryConsume_WithMultipleTokens() {
        // Configuração básica de bucket
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(10)
                .period(Duration.ofSeconds(1))
                .build();
        
        Bucket bucket = new Bucket(config);
        
        // Consome 4 tokens de uma vez
        assertTrue(bucket.tryConsume(4), "Deveria consumir 4 tokens");
        assertEquals(6, bucket.getAvailableTokens(), "Deveria ter 6 tokens restantes");
        
        // Consome mais 6 tokens (todos os restantes)
        assertTrue(bucket.tryConsume(6), "Deveria consumir os 6 tokens restantes");
        assertEquals(0, bucket.getAvailableTokens(), "Deveria ter 0 tokens disponíveis");
        
        // Tenta consumir mais 1 token
        assertFalse(bucket.tryConsume(1), "Não deveria consumir mais tokens");
        assertEquals(0, bucket.getAvailableTokens(), "Deveria continuar com 0 tokens disponíveis");
    }

    @Test
    void testTryConsume_WithGreedyRefill() throws InterruptedException {
        // Configuração com recarga gulosa
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(5)
                .period(Duration.ofMillis(100)) // Período curto para o teste
                .refillStrategy(RateLimitConfig.RefillStrategy.GREEDY)
                .build();
        
        Bucket bucket = new Bucket(config);
        
        // Consome todos os tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume());
        }
        
        // Não deve ter mais tokens
        assertFalse(bucket.tryConsume());
        
        // Espera um pouco mais que o período
        Thread.sleep(150);
        
        // Agora deve ter todos os tokens novamente (recarga gulosa)
        assertEquals(5, bucket.getAvailableTokens(), "Deveria ter 5 tokens após o período (recarga gulosa)");
        
        // Deve conseguir consumir todos os tokens novamente
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(), "Deveria consumir o token " + (i + 1) + " após recarga");
        }
    }

    @Test
    void testTryConsume_WithGradualRefill() throws InterruptedException {
        // Configuração com recarga gradual
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(10)
                .period(Duration.ofMillis(100)) // 100 ms = 10 tokens, ou seja, 1 token a cada 10ms
                .refillStrategy(RateLimitConfig.RefillStrategy.GRADUAL)
                .build();
        
        Bucket bucket = new Bucket(config);
        
        // Consome todos os tokens
        for (int i = 0; i < 10; i++) {
            assertTrue(bucket.tryConsume());
        }
        
        // Não deve ter mais tokens
        assertFalse(bucket.tryConsume());
        
        // Espera tempo suficiente para recarregar alguns tokens (mas não todos)
        Thread.sleep(50); // Deveria recarregar cerca de 5 tokens (50 ms / 10 ms por token)
        
        // Verifica que alguns tokens foram recarregados
        long tokensAfterWait = bucket.getAvailableTokens();
        assertTrue(tokensAfterWait > 0 && tokensAfterWait <= 5, 
                "Deveria ter entre 1 e 5 tokens após espera parcial, mas tinha " + tokensAfterWait);
        
        // Espera mais tempo para recarregar o restante
        Thread.sleep(60);
        
        // Agora deve ter aproximadamente 10 tokens (pode haver pequenas variações devido ao tempo de execução)
        long finalTokens = bucket.getAvailableTokens();
        assertTrue(finalTokens >= 9, 
                "Deveria ter pelo menos 9 tokens após espera completa, mas tinha " + finalTokens);
    }

    @Test
    void testEstimateWaitTime() {
        // Configuração básica de bucket
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(10)
                .period(Duration.ofSeconds(1))
                .build();
        
        Bucket bucket = new Bucket(config);
        
        // Inicialmente não deve precisar esperar
        assertEquals(0, bucket.estimateWaitTime(1), "Não deveria precisar esperar inicialmente");
        
        // Consome todos os tokens
        for (int i = 0; i < 10; i++) {
            bucket.tryConsume();
        }
        
        // Deve precisar esperar para 1 token
        long waitTime = bucket.estimateWaitTime(1);
        assertTrue(waitTime > 0, "Deveria precisar esperar para 1 token");
        
        // Tempo de espera para 5 tokens deve ser aproximadamente 5x maior
        long waitTimeForFive = bucket.estimateWaitTime(5);
        assertTrue(waitTimeForFive >= waitTime * 4.5, 
                "Tempo de espera para 5 tokens deveria ser aproximadamente 5x maior");
    }
} 