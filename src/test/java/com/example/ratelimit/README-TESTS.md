# Testes para a Biblioteca de Rate Limit

Este documento descreve os testes implementados para validar a biblioteca de Rate Limit, incluindo instruções para execução.

## Estrutura dos Testes

A suite de testes inclui:

1. **Testes unitários** - Validam o comportamento das classes individualmente
2. **Testes de integração** - Validam o comportamento do sistema como um todo
3. **Testes de carga** - Avaliam o desempenho sob condições de alta demanda

## Pré-requisitos

* Java 17+
* Maven
* Docker (necessário para os testes de integração com TestContainers)

## Execução dos Testes

### Testes Unitários e de Integração

Para executar todos os testes unitários e de integração:

```bash
mvn test
```

### Testes de Carga

Para executar os testes de carga com Gatling:

```bash
# Primeiro, inicie a aplicação
mvn spring-boot:run

# Em outro terminal, execute os testes de carga
mvn gatling:test
```

Após a execução, os relatórios de testes estarão disponíveis em:
`target/gatling/results/<TIMESTAMP>/index.html`

## Visão Geral dos Testes

### Testes Unitários

Implementados em `src/test/java/com/example/ratelimit/unit/`:

* `RateLimitAnnotationTest.java` - Testa o comportamento da anotação `@RateLimit`
  - Verifica se o limite é respeitado para cada chave
  - Confirma que o modo assíncrono bloqueia até que tokens estejam disponíveis

### Testes de Integração

Implementados em `src/test/java/com/example/ratelimit/integration/`:

* `RateLimitIntegrationTest.java` - Verifica o comportamento da API com Rate Limit
  - Valida que endpoints normais rejeitam solicitações acima do limite
  - Confirma que endpoints assíncronos bloqueiam até que tokens estejam disponíveis

### Testes de Carga

Implementados em `src/test/java/com/example/ratelimit/loadtest/`:

* `RateLimitLoadSimulation.java` - Executa simulação de carga com Gatling
  - Teste de pico para o endpoint normal (100 usuários em 30 segundos)
  - Teste constante para o endpoint assíncrono (20 usuários por segundo por 60 segundos)
  - Teste misto com ambos os endpoints (50 usuários em 45 segundos)

## Validações dos Testes

Os testes verificam:

1. **Limites de taxa** - Confirmam que os limites são respeitados
2. **Comportamento de rejeição** - Para endpoints não assíncronos
3. **Comportamento de bloqueio** - Para endpoints assíncronos
4. **Desempenho sob carga** - Tempo de resposta e taxa de sucesso
5. **Reabastecimento de tokens** - Verificam se os tokens são reabastecidos conforme configurado

## Troubleshooting

* **Erro de conexão ao Redis**: Verifique se o Docker está em execução corretamente
* **Falha em testes de integração**: O TestContainers precisa de permissões Docker adequadas
* **Falha em testes de carga**: Verifique se a aplicação está em execução na porta 8080 