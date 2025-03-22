package com.ratelimit.util;

import com.ratelimit.spi.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleKeyGeneratorTest {

    private KeyGenerator keyGenerator;

    @BeforeEach
    void setUp() {
        keyGenerator = new SimpleKeyGenerator();
    }

    @Test
    void testGenerateKey_WithoutContext_ShouldReturnResourceId() {
        String resourceId = "my-resource";
        String key = keyGenerator.generateKey(resourceId);
        
        assertEquals(resourceId, key, "Chave sem contexto deve ser igual ao resourceId");
    }

    @Test
    void testGenerateKey_WithSingleContext_ShouldCombineWithSeparator() {
        String resourceId = "my-resource";
        String context = "context1";
        String key = keyGenerator.generateKey(resourceId, context);
        
        assertEquals(resourceId + ":" + context, key, 
                "Chave com um contexto deve combinar resourceId e contexto com separador");
    }

    @Test
    void testGenerateKey_WithMultipleContext_ShouldCombineAll() {
        String resourceId = "my-resource";
        String context1 = "context1";
        String context2 = "context2";
        int context3 = 123;
        
        String key = keyGenerator.generateKey(resourceId, context1, context2, context3);
        
        assertEquals(resourceId + ":" + context1 + ":" + context2 + ":" + context3, key,
                "Chave com múltiplos contextos deve combinar todos com separador");
    }

    @Test
    void testGenerateKey_WithNullResourceId_ShouldThrowException() {
        assertThrows(NullPointerException.class, () -> keyGenerator.generateKey(null),
                "Deve lançar NullPointerException para resourceId nulo");
    }

    @Test
    void testGenerateKey_WithEmptyResourceId_ShouldAccept() {
        String key = keyGenerator.generateKey("");
        assertEquals("", key, "Deve aceitar resourceId vazio");
    }

    @Test
    void testGenerateKey_WithNullContextArray_ShouldReturnResourceId() {
        String resourceId = "my-resource";
        Object[] context = null;
        
        String key = keyGenerator.generateKey(resourceId, context);
        
        assertEquals(resourceId, key, "Chave com contexto nulo deve ser igual ao resourceId");
    }

    @Test
    void testGenerateKey_WithNullContextValues_ShouldIgnoreNull() {
        String resourceId = "my-resource";
        String key = keyGenerator.generateKey(resourceId, null, "context", null);
        
        assertEquals(resourceId + ":" + "context", key, 
                "Deve ignorar valores de contexto nulos");
    }

    @Test
    void testGenerateKey_WithAllNullContextValues_ShouldReturnResourceId() {
        String resourceId = "my-resource";
        String key = keyGenerator.generateKey(resourceId, null, null, null);
        
        assertEquals(resourceId, key, 
                "Chave com todos os valores de contexto nulos deve ser igual ao resourceId");
    }
} 