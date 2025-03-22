package com.ratelimit.builder;

import com.ratelimit.core.RateLimiter;
import com.ratelimit.core.model.RateLimitConfig;
import com.ratelimit.core.impl.DefaultRateLimiter;
import com.ratelimit.spi.KeyGenerator;
import com.ratelimit.spi.storage.BucketStore;
import com.ratelimit.util.SimpleKeyGenerator;
import com.ratelimit.adapters.inmemory.InMemoryBucketStore;

import java.util.Objects;

/**
 * Builder para configurar e instanciar um RateLimiter.
 * Permite configuração fluente de todos os componentes do rate limiter.
 */
public class RateLimiterBuilder {
    
    private BucketStore bucketStore;
    private KeyGenerator keyGenerator;
    private RateLimitConfig defaultConfig;
    
    /**
     * Cria um novo builder com valores padrão.
     */
    public RateLimiterBuilder() {
        // Valores padrão
        this.bucketStore = new InMemoryBucketStore();
        this.keyGenerator = new SimpleKeyGenerator();
        this.defaultConfig = RateLimitConfig.builder().build();
    }
    
    /**
     * Define o armazenamento de buckets a ser usado.
     * 
     * @param bucketStore implementação de BucketStore
     * @return o builder para encadeamento
     */
    public RateLimiterBuilder withBucketStore(BucketStore bucketStore) {
        this.bucketStore = Objects.requireNonNull(bucketStore, "BucketStore não pode ser nulo");
        return this;
    }
    
    /**
     * Define o gerador de chaves a ser usado.
     * 
     * @param keyGenerator implementação de KeyGenerator
     * @return o builder para encadeamento
     */
    public RateLimiterBuilder withKeyGenerator(KeyGenerator keyGenerator) {
        this.keyGenerator = Objects.requireNonNull(keyGenerator, "KeyGenerator não pode ser nulo");
        return this;
    }
    
    /**
     * Define a configuração padrão para buckets que não têm uma configuração específica.
     * 
     * @param defaultConfig configuração padrão de rate limit
     * @return o builder para encadeamento
     */
    public RateLimiterBuilder withDefaultConfig(RateLimitConfig defaultConfig) {
        this.defaultConfig = Objects.requireNonNull(defaultConfig, "Configuração padrão não pode ser nula");
        return this;
    }
    
    /**
     * Constrói uma nova instância de RateLimiter com as configurações definidas.
     * 
     * @return uma nova instância de RateLimiter
     */
    public RateLimiter build() {
        return new DefaultRateLimiter(bucketStore, keyGenerator, defaultConfig);
    }
} 