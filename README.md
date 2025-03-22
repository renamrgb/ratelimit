# Rate Limit com Redis e Prometheus/Grafana

## Sobre o Projeto
Sistema de rate limiting distribuído usando Spring Boot, Redis, Bucket4j, Prometheus e Grafana para visualização de métricas.

## Requisitos
- Java 17
- Maven
- Docker e Docker Compose

## Tecnologias Utilizadas
- **Spring Boot**: Framework para criação de aplicações Java
- **Bucket4j**: Biblioteca para implementação de rate limiting
- **Redis**: Banco de dados em memória para armazenamento distribuído dos buckets
- **Prometheus**: Coleta e armazenamento de métricas
- **Grafana**: Visualização de métricas e criação de dashboards

## Como Executar

### 1. Clonar o repositório

### 2. Compilar o projeto
```bash
mvn clean package
```

### 3. Executar com Docker Compose
```bash
docker-compose up
```
Este comando iniciará os seguintes serviços:
- Aplicação Spring Boot (porta 8080)
- Redis (porta 6379)
- Prometheus (porta 9090)
- Grafana (porta 3000)

## Monitoramento com Grafana

### Acessar o Grafana
1. Abra o navegador e acesse: `http://localhost:3000`
2. Faça login com as credenciais padrão:
   - Usuário: `admin`
   - Senha: `admin`

### Configurar a fonte de dados Prometheus
1. Navegue até Configuração > Fontes de Dados
2. Clique em "Adicionar fonte de dados"
3. Selecione "Prometheus"
4. Configure a URL: `http://prometheus:9090`
5. Clique em "Salvar & Testar"

### Importar o Dashboard para Rate Limiting
1. Navegue até Dashboards > Importar
2. Clique em "Importar" e em "Enviar JSON"
3. Cole o conteúdo do arquivo `grafana-dashboard.json` ou use o ID 13877 (JVM Micrometer) para início
4. Selecione a fonte de dados Prometheus configurada anteriormente
5. Clique em "Importar"

## Métricas Disponíveis
As seguintes métricas estão disponíveis para monitoramento:
- `ratelimit.exceeded`: Contador de eventos de limite de taxa excedido
- `ratelimit.success`: Contador de requisições bem-sucedidas com limite de taxa
- `ratelimit.response.time`: Tempo de resposta para operações com limite de taxa

## Endpoints da API
- `GET /test/limit`: Endpoint de teste com rate limiting

## Endpoints do Actuator
- `http://localhost:8080/actuator/health`: Status da aplicação
- `http://localhost:8080/actuator/prometheus`: Métricas para o Prometheus
- `http://localhost:8080/actuator/metrics`: Métricas gerais

## Próximos Passos
1. Implementar autenticação para o rate limiting baseado em usuário
2. Adicionar mais métricas específicas para diferentes endpoints
3. Configurar alarmes no Grafana para notificações de limite excedido
4. Expandir para suportar diferentes estratégias de rate limiting

# RateLimit SDK

Uma biblioteca Java modular para controle de taxa de requisições (rate limiting), com suporte para distribuição em ambientes multi-instância.

## Características

- **Independente de framework**: Core da biblioteca sem dependências externas
- **Múltiplas implementações de armazenamento**: Memória, Redis
- **API fluente**: Interface simples e expressiva
- **Suporte assíncrono**: Métodos síncronos e assíncronos
- **Extensível**: Interfaces bem definidas para extensão
- **Adaptadores para Spring**: Fácil integração com o ecossistema Spring

## Uso Básico

### Configuração Direta

```java
// Configuração básica com armazenamento em memória
RateLimiter limiter = RateLimiter.builder()
    .withBucketStore(new InMemoryBucketStore())
    .withDefaultConfig(RateLimitConfig.builder()
        .tokensPerPeriod(100)
        .period(Duration.ofMinutes(1))
        .build())
    .build();

// Verificar se pode prosseguir
if (limiter.tryAcquire("meu-recurso")) {
    // Executar operação
} else {
    // Tratar limitação de taxa
}
```

### Uso com Redis (para ambientes distribuídos)

```java
// Configurar cliente Redis
StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
    RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
);

// Criar cliente Redis para SDK
LettuceRedisClient redisClient = LettuceRedisClient.builder()
    .withConnection(connection)
    .build();

// Configurar RateLimiter com Redis
RateLimiter limiter = RateLimiter.builder()
    .withBucketStore(new RedisBucketStore(redisClient))
    .withDefaultConfig(RateLimitConfig.builder()
        .tokensPerPeriod(100)
        .period(Duration.ofMinutes(1))
        .build())
    .build();
```

### Uso Assíncrono

```java
// Versão assíncrona
limiter.tryAcquireAsync("meu-recurso")
    .thenAccept(acquired -> {
        if (acquired) {
            // Executar operação
        } else {
            // Tratar limitação de taxa
        }
    });
```

## Integração com Spring

### Configuração Automática

A biblioteca se integra automaticamente com o Spring Boot se estiver no classpath.

```yaml
# application.yml
ratelimit:
  enabled: true
  default-limit: 100
  default-duration: 60
  greedy: false
  storage: inmemory # ou 'redis'
  
  # Configuração específica para Redis
  redis:
    host: localhost
    port: 6379
    database: 0
    password: 
    timeout: 2000
    key-prefix: ratelimit:
    cache-expiry: 60000
```

### Uso com Anotações

```java
@Service
public class MeuServico {
    
    @RateLimit(limit = 10, duration = 60)
    public void operacaoLimitada() {
        // Esta operação tem limite de 10 chamadas por minuto
    }
    
    @RateLimit(limit = 100, duration = 3600, key = "recurso-especifico")
    public void outraOperacao() {
        // Esta operação usa uma chave personalizada
    }
}
```

## Arquitetura

A biblioteca segue uma arquitetura limpa com claras separações de responsabilidades:

- **Core**: Interfaces e modelos de domínio
- **SPI**: Interfaces de provedores de serviço (abstrações)
- **Adaptadores**: Implementações concretas para diferentes tecnologias
- **Builders**: API fluente para configuração

## Extensibilidade

### Criar um Novo BucketStore

```java
public class MeuBucketStore implements BucketStore {
    // Implementar os métodos da interface
}

// Usar o armazenamento personalizado
RateLimiter limiter = RateLimiter.builder()
    .withBucketStore(new MeuBucketStore())
    .build();
```

### Criar um Novo KeyGenerator

```java
public class MeuKeyGenerator implements KeyGenerator {
    @Override
    public String generateKey(String resourceId, Object... context) {
        // Lógica personalizada para geração de chaves
    }
}

// Usar o gerador personalizado
RateLimiter limiter = RateLimiter.builder()
    .withKeyGenerator(new MeuKeyGenerator())
    .build();
```

## Requisitos

- Java 8+
- Para usar Redis: Lettuce 6.0+
- Para usar Spring: Spring Boot 2.5+ 