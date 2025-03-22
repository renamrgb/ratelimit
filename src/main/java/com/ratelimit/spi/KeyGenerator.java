package com.ratelimit.spi;

/**
 * Interface para geradores de chaves utilizados no rate limiting.
 * Responsável por gerar chaves únicas para identificar recursos que estão sujeitos a limitação de taxa.
 */
public interface KeyGenerator {
    
    /**
     * Gera uma chave única para o recurso especificado e contexto opcional.
     * 
     * @param resourceId identificador do recurso
     * @param context objetos de contexto adicionais para gerar uma chave mais específica
     * @return uma chave única para o recurso
     */
    String generateKey(String resourceId, Object... context);
} 