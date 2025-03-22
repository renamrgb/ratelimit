package com.ratelimit.adapters.inmemory;

import com.ratelimit.core.model.Bucket;
import com.ratelimit.core.model.RateLimitConfig;
import com.ratelimit.spi.storage.BucketStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação de BucketStore que armazena buckets em memória.
 * Útil para ambientes de desenvolvimento e testes.
 */
public class InMemoryBucketStore implements BucketStore {
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    @Override
    public Bucket getBucket(String key, RateLimitConfig config) {
        return buckets.computeIfAbsent(key, k -> new Bucket(config));
    }
    
    @Override
    public Optional<Bucket> getBucket(String key) {
        return Optional.ofNullable(buckets.get(key));
    }
    
    @Override
    public CompletableFuture<Bucket> getBucketAsync(String key, RateLimitConfig config) {
        return CompletableFuture.completedFuture(getBucket(key, config));
    }
    
    @Override
    public void saveBucket(String key, Bucket bucket) {
        buckets.put(key, bucket);
    }
    
    @Override
    public CompletableFuture<Void> saveBucketAsync(String key, Bucket bucket) {
        saveBucket(key, bucket);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public boolean removeBucket(String key) {
        return buckets.remove(key) != null;
    }
    
    @Override
    public CompletableFuture<Boolean> removeBucketAsync(String key) {
        return CompletableFuture.completedFuture(removeBucket(key));
    }
    
    /**
     * Limpa todos os buckets armazenados.
     * Útil para testes ou reinicialização do estado.
     */
    public void clear() {
        buckets.clear();
    }
    
    /**
     * @return o número de buckets atualmente armazenados
     */
    public int size() {
        return buckets.size();
    }
} 