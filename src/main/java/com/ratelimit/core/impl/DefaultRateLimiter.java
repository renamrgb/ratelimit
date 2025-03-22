package com.ratelimit.core.impl;

import com.ratelimit.core.RateLimiter;
import com.ratelimit.core.model.Bucket;
import com.ratelimit.core.model.RateLimitConfig;
import com.ratelimit.spi.KeyGenerator;
import com.ratelimit.spi.storage.BucketStore;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Implementação padrão de RateLimiter.
 * Utiliza um BucketStore para armazenar buckets e um KeyGenerator para gerar chaves.
 */
public class DefaultRateLimiter implements RateLimiter {
    
    private final BucketStore bucketStore;
    private final KeyGenerator keyGenerator;
    private final RateLimitConfig defaultConfig;
    
    /**
     * Cria um novo rate limiter com os componentes especificados.
     * 
     * @param bucketStore armazenamento de buckets
     * @param keyGenerator gerador de chaves
     * @param defaultConfig configuração padrão
     */
    public DefaultRateLimiter(BucketStore bucketStore, KeyGenerator keyGenerator, RateLimitConfig defaultConfig) {
        this.bucketStore = Objects.requireNonNull(bucketStore, "BucketStore não pode ser nulo");
        this.keyGenerator = Objects.requireNonNull(keyGenerator, "KeyGenerator não pode ser nulo");
        this.defaultConfig = Objects.requireNonNull(defaultConfig, "Configuração padrão não pode ser nula");
    }
    
    @Override
    public boolean tryAcquire(String resourceId) {
        return tryAcquire(resourceId, 1);
    }
    
    @Override
    public boolean tryAcquire(String resourceId, int tokens) {
        Objects.requireNonNull(resourceId, "O identificador do recurso não pode ser nulo");
        if (tokens <= 0) {
            throw new IllegalArgumentException("O número de tokens deve ser maior que zero");
        }
        
        String key = keyGenerator.generateKey(resourceId);
        Bucket bucket = bucketStore.getBucket(key, defaultConfig);
        
        boolean acquired = bucket.tryConsume(tokens);
        
        // Atualiza o estado do bucket no armazenamento após a operação
        bucketStore.saveBucket(key, bucket);
        
        return acquired;
    }
    
    @Override
    public CompletableFuture<Boolean> tryAcquireAsync(String resourceId) {
        return tryAcquireAsync(resourceId, 1);
    }
    
    @Override
    public CompletableFuture<Boolean> tryAcquireAsync(String resourceId, int tokens) {
        Objects.requireNonNull(resourceId, "O identificador do recurso não pode ser nulo");
        if (tokens <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("O número de tokens deve ser maior que zero"));
        }
        
        String key = keyGenerator.generateKey(resourceId);
        
        return bucketStore.getBucketAsync(key, defaultConfig)
                .thenApply(bucket -> {
                    boolean acquired = bucket.tryConsume(tokens);
                    // Atualiza o bucket assincronamente
                    bucketStore.saveBucketAsync(key, bucket);
                    return acquired;
                });
    }
    
    /**
     * @return o armazenamento de buckets usado por este rate limiter
     */
    public BucketStore getBucketStore() {
        return bucketStore;
    }
    
    /**
     * @return o gerador de chaves usado por este rate limiter
     */
    public KeyGenerator getKeyGenerator() {
        return keyGenerator;
    }
    
    /**
     * @return a configuração padrão usada por este rate limiter
     */
    public RateLimitConfig getDefaultConfig() {
        return defaultConfig;
    }
} 