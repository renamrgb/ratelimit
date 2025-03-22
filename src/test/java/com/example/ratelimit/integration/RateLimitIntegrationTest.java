package com.example.ratelimit.integration;

import com.example.ratelimit.RatelimitTestcontainersConfig;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RatelimitTestcontainersConfig.class)
@ActiveProfiles("test")
public class RateLimitIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    public void setup() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void shouldRespectRateLimit_Normal() {
        // Primeiro, enviamos uma requisição para inicializar o sistema (descartamos o tempo desta)
        given()
            .when()
            .get("/test/normal")
            .then()
            .statusCode(HttpStatus.OK.value());
            
        // Agora, aguardamos um pouco para garantir que os tokens sejam reabastecidos
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
            
        // Teste para o endpoint normal com rate limit de 10 requisições por segundo
        List<Response> responses = new ArrayList<>();
        List<Long> responseTimes = new ArrayList<>();

        // Enviamos 30 requisições (muito acima do limite de 10) em rápida sucessão, sem pausas
        for (int i = 0; i < 30; i++) {
            long startTime = System.currentTimeMillis();
            Response response = given()
                    .when()
                    .get("/test/normal")
                    .then()
                    .extract()
                    .response();
            long endTime = System.currentTimeMillis();
            
            responses.add(response);
            responseTimes.add(endTime - startTime);
            
            System.out.println("Requisição normal " + i + ": " + responseTimes.get(i) + "ms, status: " + response.getStatusCode());
        }

        // Verifica que todas as requisições foram bem-sucedidas (pois o modo de bloqueio espera pelos tokens)
        for (Response response : responses) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
            assertThat(response.getBody().asString()).contains("Test Normal");
        }

        // Verifica se pelo menos uma requisição demorou mais de 50ms
        // Isso indica que o sistema de rate limit está em funcionamento
        boolean algumaRequisicaoLenta = responseTimes.stream().anyMatch(tempo -> tempo > 50);
        
        // Imprime um relatório dos tempos para debugging
        System.out.println("\n=== Relatório de tempos de resposta ===");
        for (int i = 0; i < responseTimes.size(); i++) {
            System.out.println("Requisição " + i + ": " + responseTimes.get(i) + "ms");
        }
        System.out.println("===================================\n");
        
        assertThat(algumaRequisicaoLenta).as("Pelo menos uma requisição deve ter demorado mais de 50ms, indicando rate limiting").isTrue();

        // Espera alguns segundos para os tokens serem reabastecidos
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long startTime = System.currentTimeMillis();
            Response response = given()
                    .when()
                    .get("/test/normal")
                    .then()
                    .extract()
                    .response();
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            
            System.out.println("Requisição após espera: " + responseTime + "ms");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        });
    }

    @Test
    void shouldBlockAndWaitForTokens_Async() {
        // Teste para o endpoint assíncrono com rate limit de 3 requisições por segundo
        List<Response> responses = new ArrayList<>();
        List<Long> responseTimes = new ArrayList<>();

        // Envia 6 requisições (acima do limite de 3)
        for (int i = 0; i < 6; i++) {
            long startTime = System.currentTimeMillis();
            Response response = given()
                    .when()
                    .get("/test/async")
                    .then()
                    .extract()
                    .response();
            long endTime = System.currentTimeMillis();
            
            responses.add(response);
            responseTimes.add(endTime - startTime);
            
            // Imprime tempo de resposta para debug
            System.out.println("Requisição assíncrona " + i + ": " + responseTimes.get(i) + "ms");
        }

        // Verifica que todas as requisições foram bem-sucedidas (pois o modo assíncrono espera pelos tokens)
        for (int i = 0; i < 6; i++) {
            assertThat(responses.get(i).getStatusCode()).isEqualTo(HttpStatus.OK.value());
            assertThat(responses.get(i).getBody().asString()).contains("Teste com Rate Limit Assíncrono");
        }

        // Verifica que as primeiras 3 requisições foram mais rápidas que as últimas 3
        double mediaTemposPrimeiras = responseTimes.subList(0, 3).stream().mapToLong(Long::longValue).average().orElse(0);
        double mediaTemposUltimas = responseTimes.subList(3, 6).stream().mapToLong(Long::longValue).average().orElse(0);
        
        assertThat(mediaTemposUltimas).as("Média dos tempos das últimas requisições deve ser maior").isGreaterThan(mediaTemposPrimeiras);
    }
} 