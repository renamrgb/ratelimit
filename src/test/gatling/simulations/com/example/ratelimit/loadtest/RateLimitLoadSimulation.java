//

package com.example.ratelimit.loadtest;

/**
 * IMPORTANTE: Este arquivo contém imports que serão resolvidos apenas durante a execução do plugin Gatling.
 * Os erros de importação são esperados e serão resolvidos automaticamente quando o teste for executado
 * usando o comando: mvn gatling:test
 * 
 * As dependências do Gatling são gerenciadas pelo plugin gatling-maven-plugin e não precisam ser
 * incluídas no classpath de compilação do projeto.
 */

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Simulação de teste de carga para validar o comportamento do Rate Limit.
 * Esta classe estende Simulation do Gatling e será detectada e executada pelo plugin Gatling.
 */
public class RateLimitLoadSimulation extends Simulation {

    // Configuração HTTP
    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json")
            .userAgentHeader("Gatling/Performance Test");

    // Cenário para o endpoint normal (não assíncrono) - deve rejeitar após o limite
    private final ScenarioBuilder normalEndpointScenario = scenario("Normal Rate Limit Test")
            .exec(
                http("Normal Request")
                    .get("/test/normal")
                    .check(
                        status().in(200, 429),
                        bodyString().saveAs("responseBody")
                    )
            )
            .exec(session -> {
                int statusCode = session.getResponse("Normal Request").status();
                System.out.println("Status Code: " + statusCode);
                return session;
            });

    // Cenário para o endpoint assíncrono - deve bloquear e esperar por tokens
    private final ScenarioBuilder asyncEndpointScenario = scenario("Async Rate Limit Test")
            .exec(
                http("Async Request")
                    .get("/test/async")
                    .check(
                        status().is(200),
                        bodyString().saveAs("responseBody")
                    )
            )
            .exec(session -> {
                long responseTime = session.getResponse("Async Request").timings().responseTime().toMillis();
                System.out.println("Response Time: " + responseTime + "ms");
                return session;
            });

    // Cenário misto - combinando os dois endpoints
    private final ScenarioBuilder mixedEndpointScenario = scenario("Mixed Endpoints Test")
            .randomSwitch()
            .on(
                Choice.withWeight(70, 
                    exec(http("Normal Request in Mixed")
                        .get("/test/normal")
                        .check(status().in(200, 429)))
                ),
                Choice.withWeight(30, 
                    exec(http("Async Request in Mixed")
                        .get("/test/async")
                        .check(status().is(200)))
                )
            );

    {
        // Configurar a simulação para o endpoint normal
        setUp(
            // Teste de pico para o endpoint normal - 100 usuários durante 30 segundos
            normalEndpointScenario.injectOpen(
                rampUsers(100).during(Duration.ofSeconds(30))
            ).protocols(httpProtocol),
            
            // Teste constante para o endpoint assíncrono - 20 usuários durante 60 segundos
            asyncEndpointScenario.injectOpen(
                constantUsersPerSec(20).during(Duration.ofSeconds(60))
            ).protocols(httpProtocol),
            
            // Teste misto para ambos os endpoints - aumento gradual até 50 usuários
            mixedEndpointScenario.injectOpen(
                rampUsers(50).during(Duration.ofSeconds(45))
            ).protocols(httpProtocol)
        )
        .assertions(
            // Validações gerais
            global().responseTime().max().lt(3000),
            global().successfulRequests().percent().gte(70),
            
            // Validações específicas por endpoint
            forAll().responseTime().mean().lt(1000),
            details("Normal Request").failedRequests().count().gt(0) // Deve ter falhas (429) no endpoint normal
        );
    }
} 
