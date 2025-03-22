package com.example.ratelimit.sdk.service.factory;

import com.example.ratelimit.sdk.annotation.RateLimit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;

import java.time.Duration;

/**
 * Interface para fábrica de buckets que abstrai a criação de buckets para permitir
 * diferentes implementações em diferentes ambientes
 */
public interface BucketFactory {
    
    /**
     * Cria um bucket para uma chave específica com as configurações da anotação
     * @param key Chave para identificar o bucket
     * @param rateLimit Anotação com as configurações do rate limit
     * @return Bucket configurado
     */
    Bucket createBucket(String key, RateLimit rateLimit);
    
    /**
     * Cria a configuração de um bucket com base na anotação de rate limit
     * @param rateLimit Anotação com as configurações do rate limit
     * @return Configuração do bucket
     */
    BucketConfiguration createBucketConfiguration(RateLimit rateLimit);
    
    /**
     * Cria um bandwidth (limite de taxa) com base na anotação de rate limit
     * @param rateLimit Anotação com as configurações do rate limit
     * @return Bandwidth configurado
     */
    Bandwidth createBandwidth(RateLimit rateLimit);
    
    /**
     * Retorna a duração em nanosegundos para a unidade de tempo especificada
     * @param timeUnit Unidade de tempo da anotação
     * @return Duração em nanosegundos
     */
    long getTimeInNanos(RateLimit.TimeUnit timeUnit);
} 