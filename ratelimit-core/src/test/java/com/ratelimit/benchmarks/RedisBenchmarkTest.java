package com.ratelimit.benchmarks;

import com.ratelimit.adapters.inmemory.InMemoryBucketStore;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark de desempenho para comparar in-memory vs Redis.
 * Deve ser executado apenas manualmente, não durante builds automáticos.
 * Use a variável de ambiente RUN_BENCHMARK=true para habilitar.
 */
@EnabledIfEnvironmentVariable(named = "RUN_BENCHMARK", matches = "true")
class RedisBenchmarkTest {

    private static final int REDIS_PORT = 6379;
    private static GenericContainer<?> redisContainer;
    private static RedisClient redisClient;
    private static StatefulRedisConnection<String, byte[]> redisConnection;
    private static io.lettuce.core.RedisClient lettuceClient;
    
    private static final int TOKENS_PER_BUCKET = 100;
    private static final int OPERATION_COUNT = 10000;
    private static final int THREAD_COUNT = 10;

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

    @Test
    void benchmarkInMemoryVsRedis() throws Exception {
        // Configuração padrão para ambos os limitadores
        RateLimitConfig config = RateLimitConfig.builder()
                .tokensPerPeriod(TOKENS_PER_BUCKET)
                .period(Duration.ofSeconds(1))
                .build();
        
        // Criar limitador em memória
        BucketStore inMemoryStore = new InMemoryBucketStore();
        RateLimiter inMemoryLimiter = RateLimiter.builder()
                .withBucketStore(inMemoryStore)
                .withKeyGenerator(new SimpleKeyGenerator())
                .withDefaultConfig(config)
                .build();
                
        // Criar limitador Redis
        BucketStore redisStore = new RedisBucketStore(redisClient);
        RateLimiter redisLimiter = RateLimiter.builder()
                .withBucketStore(redisStore)
                .withKeyGenerator(new SimpleKeyGenerator())
                .withDefaultConfig(config)
                .build();
        
        // Limpar Redis antes de executar
        redisConnection.sync().flushall();
        
        // Executar benchmarks
        System.out.println("==== BENCHMARK RATE LIMITER ====");
        
        System.out.println("\n1. Benchmark síncrono (single-thread)");
        runSyncBenchmark(inMemoryLimiter, "InMemory");
        runSyncBenchmark(redisLimiter, "Redis");
        
        System.out.println("\n2. Benchmark assíncrono (single-thread)");
        runAsyncBenchmark(inMemoryLimiter, "InMemory");
        runAsyncBenchmark(redisLimiter, "Redis");
        
        System.out.println("\n3. Benchmark multi-thread síncrono");
        runMultiThreadBenchmark(inMemoryLimiter, "InMemory", false);
        runMultiThreadBenchmark(redisLimiter, "Redis", false);
        
        System.out.println("\n4. Benchmark multi-thread assíncrono");
        runMultiThreadBenchmark(inMemoryLimiter, "InMemory", true);
        runMultiThreadBenchmark(redisLimiter, "Redis", true);
    }
    
    private void runSyncBenchmark(RateLimiter limiter, String name) {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < OPERATION_COUNT; i++) {
            String resourceId = "sync-benchmark-" + (i % 100);
            limiter.tryAcquire(resourceId);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double opsPerSec = OPERATION_COUNT * 1000.0 / duration;
        
        System.out.printf("  %s: %d operações em %d ms (%.2f ops/sec)\n", 
                name, OPERATION_COUNT, duration, opsPerSec);
    }
    
    private void runAsyncBenchmark(RateLimiter limiter, String name) throws Exception {
        long startTime = System.currentTimeMillis();
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(OPERATION_COUNT);
        
        for (int i = 0; i < OPERATION_COUNT; i++) {
            String resourceId = "async-benchmark-" + (i % 100);
            futures.add(limiter.tryAcquireAsync(resourceId));
        }
        
        // Aguardar conclusão
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double opsPerSec = OPERATION_COUNT * 1000.0 / duration;
        
        System.out.printf("  %s: %d operações em %d ms (%.2f ops/sec)\n", 
                name, OPERATION_COUNT, duration, opsPerSec);
    }
    
    private void runMultiThreadBenchmark(RateLimiter limiter, String name, boolean async) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        int opsPerThread = OPERATION_COUNT / THREAD_COUNT;
        AtomicInteger resourceCounter = new AtomicInteger(0);
        
        List<Future<Long>> threadResults = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        for (int t = 0; t < THREAD_COUNT; t++) {
            threadResults.add(executor.submit(() -> {
                long threadStart = System.currentTimeMillis();
                
                if (async) {
                    List<CompletableFuture<Boolean>> futures = new ArrayList<>(opsPerThread);
                    for (int i = 0; i < opsPerThread; i++) {
                        String resourceId = "mt-async-" + (resourceCounter.getAndIncrement() % 100);
                        futures.add(limiter.tryAcquireAsync(resourceId));
                    }
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } else {
                    for (int i = 0; i < opsPerThread; i++) {
                        String resourceId = "mt-sync-" + (resourceCounter.getAndIncrement() % 100);
                        limiter.tryAcquire(resourceId);
                    }
                }
                
                return System.currentTimeMillis() - threadStart;
            }));
        }
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        
        long totalDuration = System.currentTimeMillis() - startTime;
        
        // Calcular estatísticas por thread
        long totalThreadTime = 0;
        long maxThreadTime = 0;
        long minThreadTime = Long.MAX_VALUE;
        
        for (Future<Long> result : threadResults) {
            long threadTime = result.get();
            totalThreadTime += threadTime;
            maxThreadTime = Math.max(maxThreadTime, threadTime);
            minThreadTime = Math.min(minThreadTime, threadTime);
        }
        
        double avgThreadTime = totalThreadTime / (double) THREAD_COUNT;
        double opsPerSec = OPERATION_COUNT * 1000.0 / totalDuration;
        
        System.out.printf("  %s (%s): %d operações em %d ms (%.2f ops/sec)\n", 
                name, async ? "async" : "sync", OPERATION_COUNT, totalDuration, opsPerSec);
        System.out.printf("    Tempo por thread - Min: %d ms, Máx: %d ms, Média: %.2f ms\n",
                minThreadTime, maxThreadTime, avgThreadTime);
    }
} 