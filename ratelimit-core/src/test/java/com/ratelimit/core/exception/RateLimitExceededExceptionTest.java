package com.ratelimit.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitExceededExceptionTest {

    @Test
    void testConstructor_WithOnlyMessage() {
        String message = "Limite de taxa excedido";
        RateLimitExceededException exception = new RateLimitExceededException(message);
        
        assertEquals(message, exception.getMessage(), "A mensagem deve ser a mesma");
        assertNull(exception.getResourceId(), "O resourceId deve ser nulo");
        assertEquals(-1, exception.getWaitTimeMillis(), "O tempo de espera deve ser -1");
    }

    @Test
    void testConstructor_WithMessageAndResourceId() {
        String message = "Limite de taxa excedido";
        String resourceId = "meu-recurso";
        RateLimitExceededException exception = new RateLimitExceededException(message, resourceId);
        
        assertEquals(message, exception.getMessage(), "A mensagem deve ser a mesma");
        assertEquals(resourceId, exception.getResourceId(), "O resourceId deve ser o mesmo");
        assertEquals(-1, exception.getWaitTimeMillis(), "O tempo de espera deve ser -1");
    }

    @Test
    void testConstructor_WithMessageResourceIdAndWaitTime() {
        String message = "Limite de taxa excedido";
        String resourceId = "meu-recurso";
        long waitTimeMillis = 5000;
        
        RateLimitExceededException exception = new RateLimitExceededException(
                message, resourceId, waitTimeMillis);
        
        assertEquals(message, exception.getMessage(), "A mensagem deve ser a mesma");
        assertEquals(resourceId, exception.getResourceId(), "O resourceId deve ser o mesmo");
        assertEquals(waitTimeMillis, exception.getWaitTimeMillis(), "O tempo de espera deve ser o mesmo");
    }

    @Test
    void testGetResourceId_WithNullResourceId() {
        RateLimitExceededException exception = new RateLimitExceededException("Mensagem");
        
        assertNull(exception.getResourceId(), "O resourceId deve ser nulo");
    }

    @Test
    void testGetWaitTimeMillis_WithDefaultValue() {
        RateLimitExceededException exception = new RateLimitExceededException("Mensagem");
        
        assertEquals(-1, exception.getWaitTimeMillis(), 
                "O tempo de espera padrão deve ser -1 (não estimado)");
    }

    @Test
    void testGetWaitTimeMillis_WithZeroWaitTime() {
        RateLimitExceededException exception = new RateLimitExceededException(
                "Mensagem", "recurso", 0);
        
        assertEquals(0, exception.getWaitTimeMillis(), 
                "Tempo de espera zero deve ser permitido");
    }

    @Test
    void testGetWaitTimeMillis_WithNegativeWaitTime() {
        RateLimitExceededException exception = new RateLimitExceededException(
                "Mensagem", "recurso", -100);
        
        assertEquals(-100, exception.getWaitTimeMillis(), 
                "Tempo de espera negativo deve ser permitido");
    }
} 