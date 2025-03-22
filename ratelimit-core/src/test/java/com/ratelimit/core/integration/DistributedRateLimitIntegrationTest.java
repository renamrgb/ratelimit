package com.ratelimit.core.integration;

import com.ratelimit.adapters.redis.LettuceRedisClient;
import com.ratelimit.adapters.redis.RedisBucketStore;
import com.ratelimit.core.RateLimiter;
import com.ratelimit.core.model.RateLimitConfig;
import com.ratelimit.spi.BucketStore;
import com.ratelimit.spi.RedisClient;
import com.ratelimit.util.SimpleKeyGenerator;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DistributedRateLimitIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static GenericContainer<?> redisContainer;
    private static RedisClient redisClient;
    private static StatefulRedisConnection<String, byte[]> redisConnection;
    private static io.lettuce.core.RedisClient lettuceClient;
    
    private RateLimiter rateLimiter;
    private static final String RESOURCE_ID = "test-resource";

    @BeforeAll
    static void setupRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(REDIS_PORT);
        redisContainer.start();
        
        RedisURI redisUri = RedisURI.builder()
                .withHost(redisContainer.getHost())
                .withPort(redisContainer.getMappedPort(REDIS_PORT))
                .build();
        
        lettuceClient = RedisClient.create(redisUri);
        redisConnection = lettuceClient.connect(LettuceRedisClient.CODEC);
        redisClient = LettuceRedisClient.builder()
                .withConnection(redisConnection)
                .build();
    }
    
    @AfterAll
    static void tearDown() {
        if (redisConnection != null) {
            redisConnection.close();
        }
        if (lettuceClient != null) {
            lettuceClient.shutdown();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }
    
    @BeforeEach
    void setUp() {
        // Limpar Redis antes de cada teste
        redisConnection.sync().flushall();
        
        BucketStore bucketStore = new RedisBucketStore(redisClient);
        
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(5)
                .period(Duration.ofSeconds(1))
                .build();
        
        rateLimiter = RateLimiter.builder()
                .withBucketStore(bucketStore)
                .withKeyGenerator(new SimpleKeyGenerator())
                .withDefaultConfig(config)
                .build();
    }

    @Test
    void testDistributedRateLimiting_SimpleAcquire() {
        // Teste básico de obtenção de tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire(RESOURCE_ID), 
                    "Deveria adquirir token " + (i+1));
        }
        
        // Não deve haver mais tokens
        assertFalse(rateLimiter.tryAcquire(RESOURCE_ID), 
                "Não deveria permitir exceder o limite");
    }
    
    @Test
    void testDistributedRateLimiting_AsyncAcquire() throws Exception {
        // Teste assíncrono de obtenção de tokens
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            futures.add(rateLimiter.tryAcquireAsync(RESOURCE_ID));
        }
        
        // Verificar todos os resultados
        for (int i = 0; i < 5; i++) {
            assertTrue(futures.get(i).get(), 
                    "Deveria adquirir token assíncrono " + (i+1));
        }
        
        // O próximo pedido deve falhar
        assertFalse(rateLimiter.tryAcquireAsync(RESOURCE_ID).get(),
                "Não deveria permitir exceder o limite assincronamente");
    }
    
    @Test
    void testDistributedRateLimiting_WithMultipleClients() throws Exception {
        // Criar vários clientes para simular um ambiente distribuído
        int numClients = 3;
        List<RateLimiter> rateLimiters = new ArrayList<>();
        
        for (int i = 0; i < numClients; i++) {
            BucketStore bucketStore = new RedisBucketStore(redisClient);
            
            RateLimitConfig config = RateLimitConfig.builder()
                    .tokensPerPeriod(5)
                    .period(Duration.ofSeconds(1))
                    .build();
            
            RateLimiter limiter = RateLimiter.builder()
                    .withBucketStore(bucketStore)
                    .withKeyGenerator(new SimpleKeyGenerator())
                    .withDefaultConfig(config)
                    .build();
            
            rateLimiters.add(limiter);
        }
        
        // Distribuir 5 requisições entre os clientes
        int acquired = 0;
        for (int i = 0; i < 10; i++) {
            int clientIndex = i % numClients;
            RateLimiter limiter = rateLimiters.get(clientIndex);
            
            if (limiter.tryAcquire(RESOURCE_ID)) {
                acquired++;
            }
        }
        
        // Deveria ter conseguido exatamente 5 tokens, independente de qual cliente
        assertEquals(5, acquired, "Deveria limitar a exatamente 5 tokens no total");
    }
    
    @Test
    void testDistributedRateLimiting_WithConcurrentRequests() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int totalRequests = 20;
        List<Future<Boolean>> results = new ArrayList<>();
        
        // Enviar múltiplas requisições concorrentes
        for (int i = 0; i < totalRequests; i++) {
            results.add(executor.submit(() -> rateLimiter.tryAcquire(RESOURCE_ID)));
        }
        
        // Aguardar todos os resultados
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Contar quantos conseguiram adquirir tokens
        int successCount = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                successCount++;
            }
        }
        
        // Deveria ter limitado a 5 tokens com sucesso
        assertEquals(5, successCount, 
                "Deveria ter limitado a 5 aquisições bem-sucedidas mesmo com requisições concorrentes");
    }
    
    @Test
    void testDistributedRateLimiting_TokenRefill() throws Exception {
        // Consumir todos os tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire(RESOURCE_ID));
        }
        
        // Verificar que não há mais tokens
        assertFalse(rateLimiter.tryAcquire(RESOURCE_ID));
        
        // Aguardar o tempo de recarga (um pouco mais que o período)
        Thread.sleep(1100);
        
        // Deveria ter tokens novamente
        assertTrue(rateLimiter.tryAcquire(RESOURCE_ID), 
                "Deveria ter tokens recarregados após o período");
    }
} 