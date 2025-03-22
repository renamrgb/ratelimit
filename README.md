# SDK de Rate Limit com Redis

Este projeto implementa um SDK para controle de rate limit em aplicações Spring Boot, utilizando Redis como armazenamento distribuído e oferecendo suporte a operações assíncronas.

## Características

- **Rate Limit Distribuído**: Utiliza o Redis como armazenamento para garantir que o rate limit seja respeitado mesmo em ambiente com múltiplas instâncias.
- **Suporte Assíncrono**: Permite operações assíncronas para melhor desempenho em aplicações com alta carga.
- **Configuração Flexível**: Diversas opções de configuração via propriedades.
- **Suporte a Overdraft**: Capacidade para lidar com picos de tráfego temporários.
- **Opções de Refill**: Modos greedy ou interval para recarga de tokens.
- **Cache de Buckets Local**: Melhor performance com cache em memória.
- **Métricas com Micrometer**: Monitoramento detalhado do funcionamento do rate limit.
- **Sistema de Retry**: Com backoff exponencial para tratamento de falhas.

## Tecnologias

- Spring Boot 3.3.5
- Redis com Lettuce
- Bucket4j 8.14.0
- Micrometer 1.11.5

## Uso da Anotação

```java
// Rate Limit Simples
@RateLimit(
    key = "test-normal",
    limit = 10,
    timeUnit = RateLimit.TimeUnit.SECONDS
)
public ResponseEntity<String> normalTest() {
    return ResponseEntity.ok("Test Normal");
}

// Rate Limit Assíncrono
@RateLimit(
    key = "test-async",
    limit = 10,
    timeUnit = RateLimit.TimeUnit.SECONDS,
    async = true
)
public ResponseEntity<String> asyncTest() {
    return ResponseEntity.ok("Teste Assíncrono");
}

// Rate Limit com Retry e Fallback
@RateLimit(
    key = "test-retry",
    limit = 5,
    timeUnit = RateLimit.TimeUnit.MINUTES,
    overdraft = 2,
    greedyRefill = true,
    retry = @Retry(
        maxAttempts = 3,
        include = {RuntimeException.class},
        fallbackMethod = "fallback",
        backoff = @BackoffRetry(
            delay = 500,
            multiplier = 2
        )
    )
)
public ResponseEntity<String> retryTest() {
    // ... implementação
}

// Método de fallback
public ResponseEntity<String> fallback(Throwable throwable) {
    return ResponseEntity.ok("Fallback: " + throwable.getMessage());
}
```

## Configuração

No arquivo `application.properties`:

```properties
# Redis
redis.host=localhost
redis.port=6379
redis.timeout=2000
redis.database=0
redis.password=

# Rate Limit
rate-limit.enable-metrics=true
rate-limit.cache-expiry-seconds=300
```

## Implementação Assíncrona

A implementação utiliza o cliente Lettuce para Redis, que oferece suporte nativo a operações assíncronas. Quando a propriedade `async = true` é definida na anotação `@RateLimit`, as operações de consumo de tokens são realizadas de forma assíncrona, utilizando CompletableFuture.

Isso é especialmente útil em aplicações com alta carga, pois permite:

1. Não bloquear a thread principal durante operações de I/O com o Redis
2. Maior throughput para aplicações com muitas requisições concorrentes
3. Melhor aproveitamento de recursos do sistema

## Implementação com Lettuce

O cliente Lettuce foi escolhido por suportar operações assíncronas de forma nativa e por ser o cliente Redis recomendado para aplicações Spring Boot modernas. Ele oferece:

- Melhor desempenho em comparação com Jedis
- Suporte nativo a operações assíncronas
- Gerenciamento automático de conexões
- Compatibilidade com recursos modernos do Redis
- API mais amigável e orientada a reatividade 