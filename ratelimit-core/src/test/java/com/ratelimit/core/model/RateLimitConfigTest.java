package com.ratelimit.core.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitConfigTest {

    @Test
    void testDefaultConfigValues() {
        RateLimitConfig config = RateLimitConfig.builder().build();
        
        assertEquals(1, config.getTokensPerPeriod(), "O número padrão de tokens deve ser 1");
        assertEquals(Duration.ofSeconds(1), config.getPeriod(), "O período padrão deve ser 1 segundo");
        assertEquals(RateLimitConfig.RefillStrategy.GRADUAL, config.getRefillStrategy(), 
                "A estratégia de recarga padrão deve ser GRADUAL");
    }

    @Test
    void testCustomConfigValues() {
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(100)
                .period(Duration.ofMinutes(5))
                .refillStrategy(RateLimitConfig.RefillStrategy.GREEDY)
                .build();
        
        assertEquals(100, config.getTokensPerPeriod(), "O número de tokens deve ser 100");
        assertEquals(Duration.ofMinutes(5), config.getPeriod(), "O período deve ser 5 minutos");
        assertEquals(RateLimitConfig.RefillStrategy.GREEDY, config.getRefillStrategy(), 
                "A estratégia de recarga deve ser GREEDY");
    }

    @Test
    void testBuilder_WithIllegalArguments() {
        // Teste com tokens inválidos
        assertThrows(IllegalArgumentException.class, () -> 
                RateLimitConfig.builder().tokensPerPeriod(0).build(),
                "Deveria lançar IllegalArgumentException para tokens = 0");
        
        assertThrows(IllegalArgumentException.class, () -> 
                RateLimitConfig.builder().tokensPerPeriod(-1).build(),
                "Deveria lançar IllegalArgumentException para tokens < 0");
        
        // Teste com período inválido
        assertThrows(NullPointerException.class, () -> 
                RateLimitConfig.builder().period(null).build(),
                "Deveria lançar NullPointerException para período nulo");
        
        assertThrows(IllegalArgumentException.class, () -> 
                RateLimitConfig.builder().period(Duration.ZERO).build(),
                "Deveria lançar IllegalArgumentException para período = 0");
        
        assertThrows(IllegalArgumentException.class, () -> 
                RateLimitConfig.builder().period(Duration.ofSeconds(-1)).build(),
                "Deveria lançar IllegalArgumentException para período < 0");
        
        // Teste com estratégia de recarga inválida
        assertThrows(NullPointerException.class, () -> 
                RateLimitConfig.builder().refillStrategy(null).build(),
                "Deveria lançar NullPointerException para refillStrategy nula");
    }

    @Test
    void testRefillStrategy_Values() {
        // Verifica se existem apenas os dois valores esperados
        RateLimitConfig.RefillStrategy[] strategies = RateLimitConfig.RefillStrategy.values();
        assertEquals(2, strategies.length, "Deve haver exatamente 2 estratégias de recarga");
        
        // Verifica se os valores são os esperados
        assertNotNull(RateLimitConfig.RefillStrategy.valueOf("GRADUAL"), 
                "Deve existir a estratégia GRADUAL");
        assertNotNull(RateLimitConfig.RefillStrategy.valueOf("GREEDY"), 
                "Deve existir a estratégia GREEDY");
    }
} 