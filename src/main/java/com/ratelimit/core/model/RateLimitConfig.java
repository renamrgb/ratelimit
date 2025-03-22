package com.ratelimit.core.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuração para um limitador de taxa.
 * Define a quantidade de tokens, o período e a estratégia de recarga.
 */
public class RateLimitConfig {
    
    /**
     * Estratégia de recarga de tokens.
     */
    public enum RefillStrategy {
        /**
         * Recarga gradual ao longo do tempo.
         */
        GRADUAL,
        
        /**
         * Recarga total no início de cada período.
         */
        GREEDY
    }
    
    private final long tokensPerPeriod;
    private final Duration period;
    private final RefillStrategy refillStrategy;
    
    private RateLimitConfig(Builder builder) {
        this.tokensPerPeriod = builder.tokensPerPeriod;
        this.period = builder.period;
        this.refillStrategy = builder.refillStrategy;
    }
    
    /**
     * @return número de tokens disponíveis por período
     */
    public long getTokensPerPeriod() {
        return tokensPerPeriod;
    }
    
    /**
     * @return duração do período
     */
    public Duration getPeriod() {
        return period;
    }
    
    /**
     * @return estratégia de recarga de tokens
     */
    public RefillStrategy getRefillStrategy() {
        return refillStrategy;
    }
    
    /**
     * Cria um novo builder para RateLimitConfig.
     * 
     * @return uma nova instância de Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder para criar instâncias de RateLimitConfig.
     */
    public static class Builder {
        private long tokensPerPeriod = 1;
        private Duration period = Duration.ofSeconds(1);
        private RefillStrategy refillStrategy = RefillStrategy.GRADUAL;
        
        /**
         * Define o número de tokens por período.
         * 
         * @param tokensPerPeriod número de tokens
         * @return o builder para encadeamento
         */
        public Builder tokensPerPeriod(long tokensPerPeriod) {
            if (tokensPerPeriod <= 0) {
                throw new IllegalArgumentException("O número de tokens deve ser maior que zero");
            }
            this.tokensPerPeriod = tokensPerPeriod;
            return this;
        }
        
        /**
         * Define o período de tempo.
         * 
         * @param period duração do período
         * @return o builder para encadeamento
         */
        public Builder period(Duration period) {
            Objects.requireNonNull(period, "O período não pode ser nulo");
            if (period.isNegative() || period.isZero()) {
                throw new IllegalArgumentException("O período deve ser maior que zero");
            }
            this.period = period;
            return this;
        }
        
        /**
         * Define a estratégia de recarga.
         * 
         * @param refillStrategy estratégia de recarga
         * @return o builder para encadeamento
         */
        public Builder refillStrategy(RefillStrategy refillStrategy) {
            Objects.requireNonNull(refillStrategy, "A estratégia de recarga não pode ser nula");
            this.refillStrategy = refillStrategy;
            return this;
        }
        
        /**
         * Constrói uma nova instância de RateLimitConfig.
         * 
         * @return uma nova instância configurada
         */
        public RateLimitConfig build() {
            return new RateLimitConfig(this);
        }
    }
} 