package com.example.ratelimit.service;

import org.springframework.http.ResponseEntity;

/**
 * Interface que define os serviços para os endpoints de teste
 */
public interface TestService {
    
    /**
     * Teste básico com retry
     * @return ResponseEntity com mensagem de teste
     */
    ResponseEntity<String> test();
    
    /**
     * Teste com rate limit normal (bloqueante)
     * @return ResponseEntity com mensagem de teste
     */
    ResponseEntity<String> normalTest();
    
    /**
     * Teste com rate limit assíncrono
     * @return ResponseEntity com mensagem de teste
     */
    ResponseEntity<String> asyncTest();
    
    /**
     * Teste com rate limit assíncrono e retry
     * @return ResponseEntity com mensagem de teste
     */
    ResponseEntity<String> asyncRetryTest();
    
    /**
     * Método de fallback para tratamento de erros
     * @param throwable Exceção que ocorreu
     * @return ResponseEntity com mensagem de fallback
     */
    ResponseEntity<String> fallback(Throwable throwable);
} 