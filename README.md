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