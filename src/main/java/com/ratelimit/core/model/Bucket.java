package com.ratelimit.core.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Representa um bucket de tokens para o algoritmo de rate limiting.
 * Implementa o algoritmo de token bucket de forma simplificada.
 */
public class Bucket {
    
    private final RateLimitConfig config;
    private final AtomicLong availableTokens;
    private volatile long lastRefillTimestamp;
    
    /**
     * Cria um novo bucket com base na configuração especificada.
     * 
     * @param config configuração do rate limit
     */
    public Bucket(RateLimitConfig config) {
        this.config = config;
        this.availableTokens = new AtomicLong(config.getTokensPerPeriod());
        this.lastRefillTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Tenta consumir um token do bucket.
     * 
     * @return true se o token foi consumido com sucesso, false caso contrário
     */
    public boolean tryConsume() {
        return tryConsume(1);
    }
    
    /**
     * Tenta consumir múltiplos tokens do bucket.
     * 
     * @param numTokens número de tokens a consumir
     * @return true se os tokens foram consumidos com sucesso, false caso contrário
     */
    public synchronized boolean tryConsume(long numTokens) {
        refill();
        
        if (availableTokens.get() < numTokens) {
            return false;
        }
        
        availableTokens.addAndGet(-numTokens);
        return true;
    }
    
    /**
     * Calcula o tempo de espera estimado até que o número solicitado de tokens
     * esteja disponível.
     * 
     * @param numTokens número de tokens necessários
     * @return tempo estimado de espera em milissegundos, ou 0 se já disponível
     */
    public synchronized long estimateWaitTime(long numTokens) {
        refill();
        
        if (availableTokens.get() >= numTokens) {
            return 0;
        }
        
        long tokensNeeded = numTokens - availableTokens.get();
        double refillRatePerMs = (double) config.getTokensPerPeriod() / config.getPeriod().toMillis();
        
        return (long) Math.ceil(tokensNeeded / refillRatePerMs);
    }
    
    /**
     * Recarrega o bucket com base no tempo decorrido e na estratégia de recarga.
     */
    private void refill() {
        long currentTimeMillis = System.currentTimeMillis();
        long elapsedTimeMillis = currentTimeMillis - lastRefillTimestamp;
        
        if (elapsedTimeMillis <= 0) {
            return;
        }
        
        if (config.getRefillStrategy() == RateLimitConfig.RefillStrategy.GREEDY &&
            elapsedTimeMillis >= config.getPeriod().toMillis()) {
            // Para estratégia GREEDY, recarrega completamente se um período completo passou
            availableTokens.set(config.getTokensPerPeriod());
            lastRefillTimestamp = currentTimeMillis;
        } else if (config.getRefillStrategy() == RateLimitConfig.RefillStrategy.GRADUAL) {
            // Para estratégia GRADUAL, calcula os tokens a adicionar proporcionalmente ao tempo
            double refillRatePerMs = (double) config.getTokensPerPeriod() / config.getPeriod().toMillis();
            long tokensToAdd = (long) (elapsedTimeMillis * refillRatePerMs);
            
            if (tokensToAdd > 0) {
                long newTokens = Math.min(
                    config.getTokensPerPeriod(),
                    availableTokens.addAndGet(tokensToAdd)
                );
                availableTokens.set(newTokens);
                lastRefillTimestamp = currentTimeMillis;
            }
        }
    }
    
    /**
     * @return a configuração deste bucket
     */
    public RateLimitConfig getConfig() {
        return config;
    }
    
    /**
     * @return o número atual de tokens disponíveis
     */
    public long getAvailableTokens() {
        refill();
        return availableTokens.get();
    }
    
    /**
     * @return timestamp da última recarga
     */
    public Instant getLastRefillTimestamp() {
        return Instant.ofEpochMilli(lastRefillTimestamp);
    }
} 