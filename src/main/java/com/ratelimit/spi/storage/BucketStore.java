package com.ratelimit.spi.storage;

import com.ratelimit.core.model.Bucket;
import com.ratelimit.core.model.RateLimitConfig;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface para armazenamento e recuperação de buckets de rate limiting.
 */
public interface BucketStore {
    
    /**
     * Obtém um bucket para a chave especificada.
     * Se o bucket não existir e for fornecida uma configuração,
     * cria um novo bucket com essa configuração.
     * 
     * @param key chave única que identifica o bucket
     * @param config configuração para criar um bucket caso não exista
     * @return o bucket existente ou um novo bucket
     */
    Bucket getBucket(String key, RateLimitConfig config);
    
    /**
     * Obtém um bucket para a chave especificada.
     * 
     * @param key chave única que identifica o bucket
     * @return um Optional contendo o bucket, ou vazio se não existir
     */
    Optional<Bucket> getBucket(String key);
    
    /**
     * Obtém um bucket de forma assíncrona.
     * 
     * @param key chave única que identifica o bucket
     * @param config configuração para criar um bucket caso não exista
     * @return CompletableFuture com o bucket
     */
    CompletableFuture<Bucket> getBucketAsync(String key, RateLimitConfig config);
    
    /**
     * Salva um bucket no armazenamento.
     * 
     * @param key chave única que identifica o bucket
     * @param bucket o bucket a ser salvo
     */
    void saveBucket(String key, Bucket bucket);
    
    /**
     * Salva um bucket no armazenamento de forma assíncrona.
     * 
     * @param key chave única que identifica o bucket
     * @param bucket o bucket a ser salvo
     * @return CompletableFuture que completa quando a operação terminar
     */
    CompletableFuture<Void> saveBucketAsync(String key, Bucket bucket);
    
    /**
     * Remove um bucket do armazenamento.
     * 
     * @param key chave única que identifica o bucket
     * @return true se o bucket foi removido, false caso não existisse
     */
    boolean removeBucket(String key);
    
    /**
     * Remove um bucket do armazenamento de forma assíncrona.
     * 
     * @param key chave única que identifica o bucket
     * @return CompletableFuture com true se o bucket foi removido, false caso contrário
     */
    CompletableFuture<Boolean> removeBucketAsync(String key);
} 