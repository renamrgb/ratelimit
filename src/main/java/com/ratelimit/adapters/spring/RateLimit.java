package com.ratelimit.adapters.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotação para aplicar rate limiting em métodos.
 * Quando um método é anotado com @RateLimit, o SpringRateLimitAdapter
 * intercepta a chamada e aplica limitação de taxa antes da execução.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /**
     * Limite de requisições permitidas no período especificado.
     * 
     * @return número máximo de requisições permitidas
     */
    long limit() default 10;
    
    /**
     * Duração do período de limitação em segundos.
     * 
     * @return duração do período em segundos
     */
    long duration() default 1;
    
    /**
     * Chave para identificar o recurso limitado.
     * Se não for especificada, a chave padrão será "className.methodName".
     * 
     * @return chave para o bucket de rate limiting
     */
    String key() default "";
    
    /**
     * Se a estratégia de recarga deve ser gulosa (todos os tokens de uma vez no início do período)
     * ou gradual (tokens distribuídos uniformemente ao longo do período).
     * 
     * @return true para recarga gulosa, false para recarga gradual
     */
    boolean greedy() default false;
    
    /**
     * Se o rate limiting deve ser ignorado para esta chamada.
     * Útil quando a anotação é herdada ou configurada globalmente.
     * 
     * @return true para ignorar o rate limiting
     */
    boolean disabled() default false;
} 