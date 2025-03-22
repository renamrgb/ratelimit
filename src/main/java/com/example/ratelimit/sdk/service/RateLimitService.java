package com.example.ratelimit.sdk.service;

import com.example.ratelimit.sdk.annotation.RateLimit;
import io.github.bucket4j.Bucket;

/**
 * Interface que define o serviço de rate limit
 */
public interface RateLimitService {
    
    /**
     * Cria ou obtém um bucket para o rate limit
     * @param key Chave para identificar o bucket
     * @param rateLimit Anotação com as configurações de rate limit
     * @return Bucket configurado
     */
    Bucket getBucket(String key, RateLimit rateLimit);
    
    /**
     * Consome um token do bucket, bloqueando se necessário
     * @param bucket Bucket a consumir token
     * @param key Chave para logging
     */
    void consumeToken(Bucket bucket, String key);
    
    /**
     * Consome um token de forma assíncrona
     * @param bucket Bucket a consumir token
     * @param key Chave para logging
     * @return true se consumido com sucesso, false caso contrário
     * @throws InterruptedException se a thread for interrompida durante a espera
     */
    boolean consumeTokenAsync(Bucket bucket, String key) throws InterruptedException;
    
    /**
     * Cria uma chave composta para o rate limit
     * @param userKey Chave fornecida pelo usuário
     * @param methodName Nome do método
     * @return Chave completa
     */
    String generateKey(String userKey, String methodName);
} 