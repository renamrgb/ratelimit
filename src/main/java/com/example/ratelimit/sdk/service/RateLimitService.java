package com.example.ratelimit.sdk.service;

import com.example.ratelimit.sdk.annotation.RateLimit;
import io.github.bucket4j.Bucket;

import java.util.concurrent.CompletableFuture;

/**
 * Interface que define o serviço de rate limit
 */
public interface RateLimitService {
    
    /**
     * Gera uma chave única para identificar o bucket de rate limit
     *
     * @param className     Nome da classe
     * @param methodName    Nome do método
     * @param ip            IP do cliente (opcional)
     * @param userId        ID do usuário (opcional)
     * @return Chave única para o bucket
     */
    String generateKey(String className, String methodName, String ip, String userId);
    
    /**
     * Obtém o bucket para a chave e configuração especificadas
     *
     * @param key       Chave do bucket
     * @param rateLimit Configuração de rate limit
     * @return Bucket correspondente
     */
    Object getBucket(String key, RateLimit rateLimit);
    
    /**
     * Consome um token do bucket especificado
     *
     * @param key       Chave do bucket
     * @param rateLimit Configuração de rate limit
     * @param tokens    Número de tokens a consumir
     * @return true se o token foi consumido com sucesso, false caso contrário
     */
    boolean consumeToken(String key, RateLimit rateLimit, long tokens);
    
    /**
     * Consome um token do bucket especificado de forma assíncrona
     *
     * @param key       Chave do bucket
     * @param rateLimit Configuração de rate limit
     * @param tokens    Número de tokens a consumir
     * @return CompletableFuture com o resultado do consumo
     */
    CompletableFuture<Boolean> consumeTokenAsync(String key, RateLimit rateLimit, long tokens);
} 