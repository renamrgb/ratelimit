package com.example.ratelimit.unit;

import com.example.ratelimit.RatelimitTestcontainersConfig;
import com.example.ratelimit.sdk.annotation.RateLimit;
import com.example.ratelimit.sdk.aop.RateLimitAspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import({RatelimitTestcontainersConfig.class, RateLimitAnnotationTest.TestConfig.class})
@ActiveProfiles("test")
public class RateLimitAnnotationTest {

    @Autowired
    private TestService testService;

    @Test
    void testRateLimitExceeded() {
        // Configuração permite apenas 2 requisições por segundo
        
        // Primeira chamada deve funcionar
        ResponseEntity<String> response1 = testService.testMethod();
        assertThat(response1.getStatusCode().value()).isEqualTo(200);
        assertThat(response1.getBody()).isEqualTo("Success");
        
        // Segunda chamada deve funcionar
        ResponseEntity<String> response2 = testService.testMethod();
        assertThat(response2.getStatusCode().value()).isEqualTo(200);
        assertThat(response2.getBody()).isEqualTo("Success");
        
        // Terceira chamada deve falhar com exceção (acima do limite)
        Exception exception = assertThrows(HttpClientErrorException.class, () -> {
            testService.testMethod();
        });
        
        assertThat(exception.getMessage()).contains("429");
    }
    
    @Test
    void testAsyncRateLimitBlocksUntilTokensAvailable() throws InterruptedException, ExecutionException {
        // Primeira chamada deve ser rápida
        long startTime1 = System.currentTimeMillis();
        ResponseEntity<String> response1 = testService.asyncTestMethod();
        long endTime1 = System.currentTimeMillis();
        
        // Segunda chamada deve ser rápida
        long startTime2 = System.currentTimeMillis();
        ResponseEntity<String> response2 = testService.asyncTestMethod();
        long endTime2 = System.currentTimeMillis();
        
        // Terceira chamada deve bloquear até haver tokens disponíveis
        long startTime3 = System.currentTimeMillis();
        ResponseEntity<String> response3 = testService.asyncTestMethod();
        long endTime3 = System.currentTimeMillis();
        
        // Todas as chamadas devem ter sucesso
        assertThat(response1.getStatusCode().value()).isEqualTo(200);
        assertThat(response2.getStatusCode().value()).isEqualTo(200);
        assertThat(response3.getStatusCode().value()).isEqualTo(200);
        
        // As duas primeiras chamadas devem ser rápidas
        assertThat(endTime1 - startTime1).isLessThan(100);
        assertThat(endTime2 - startTime2).isLessThan(100);
        
        // A terceira chamada deve ser mais lenta
        assertThat(endTime3 - startTime3).isGreaterThan(300);
    }
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestService testService() {
            return new TestService();
        }
    }
    
    @Service
    static class TestService {
        
        @RateLimit(key = "test-unit", limit = 2, timeUnit = RateLimit.TimeUnit.SECONDS)
        public ResponseEntity<String> testMethod() {
            return ResponseEntity.ok("Success");
        }
        
        @RateLimit(key = "test-unit-async", limit = 2, timeUnit = RateLimit.TimeUnit.SECONDS, async = true)
        public ResponseEntity<String> asyncTestMethod() {
            return ResponseEntity.ok("Async Success");
        }
    }
} 