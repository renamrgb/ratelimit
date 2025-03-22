package com.ratelimit.core;

import com.ratelimit.builder.RateLimiterBuilder;

import java.util.concurrent.CompletableFuture;

/**
 * Interface principal para o controle de rate limiting.
 * Fornece métodos para verificar se uma operação pode ser executada
 * dentro dos limites de taxa definidos.
 */
public interface RateLimiter {
    
    /**
     * Tenta adquirir uma permissão (token) para o recurso especificado.
     * 
     * @param resourceId identificador do recurso
     * @return true se a permissão foi concedida, false caso contrário
     */
    boolean tryAcquire(String resourceId);
    
    /**
     * Tenta adquirir múltiplas permissões (tokens) para o recurso especificado.
     * 
     * @param resourceId identificador do recurso
     * @param tokens número de tokens a serem consumidos
     * @return true se as permissões foram concedidas, false caso contrário
     */
    boolean tryAcquire(String resourceId, int tokens);
    
    /**
     * Tenta adquirir uma permissão (token) de forma assíncrona.
     * 
     * @param resourceId identificador do recurso
     * @return CompletableFuture com o resultado da operação (true se concedido, false caso contrário)
     */
    CompletableFuture<Boolean> tryAcquireAsync(String resourceId);
    
    /**
     * Tenta adquirir múltiplas permissões (tokens) de forma assíncrona.
     * 
     * @param resourceId identificador do recurso
     * @param tokens número de tokens a serem consumidos
     * @return CompletableFuture com o resultado da operação (true se concedido, false caso contrário)
     */
    CompletableFuture<Boolean> tryAcquireAsync(String resourceId, int tokens);
    
    /**
     * Cria um builder para configurar e instanciar um RateLimiter.
     * 
     * @return uma nova instância de RateLimiterBuilder
     */
    static RateLimiterBuilder builder() {
        return new RateLimiterBuilder();
    }
} 