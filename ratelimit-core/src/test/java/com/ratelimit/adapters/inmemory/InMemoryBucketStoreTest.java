package com.ratelimit.adapters.inmemory;

import com.ratelimit.core.model.Bucket;
import com.ratelimit.core.model.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryBucketStoreTest {

    private static final String TEST_KEY = "test-key";
    private InMemoryBucketStore bucketStore;
    private RateLimitConfig config;

    @BeforeEach
    void setUp() {
        bucketStore = new InMemoryBucketStore();
        config = RateLimitConfig.builder()
                .tokensPerPeriod(10)
                .period(Duration.ofSeconds(1))
                .build();
    }

    @Test
    void testGetBucket_WhenBucketDoesNotExist_ShouldCreateNew() {
        // O bucket não existe inicialmente
        Bucket bucket = bucketStore.getBucket(TEST_KEY, config);
        
        assertNotNull(bucket, "Deve criar um novo bucket quando não existe");
        assertEquals(config, bucket.getConfig(), "O bucket deve usar a configuração fornecida");
        assertEquals(10, bucket.getAvailableTokens(), "O bucket deve ter o número inicial de tokens");
    }

    @Test
    void testGetBucket_WhenBucketExists_ShouldReturnExisting() {
        // Cria o bucket primeiro
        Bucket originalBucket = bucketStore.getBucket(TEST_KEY, config);
        
        // Consome alguns tokens
        originalBucket.tryConsume(3);
        
        // Obtém o bucket novamente
        Bucket retrievedBucket = bucketStore.getBucket(TEST_KEY, config);
        
        assertNotNull(retrievedBucket, "Deve retornar um bucket");
        assertSame(originalBucket, retrievedBucket, "Deve retornar o mesmo bucket original");
        assertEquals(7, retrievedBucket.getAvailableTokens(), "O bucket deve manter seu estado");
    }

    @Test
    void testGetBucketOptional_WhenBucketDoesNotExist_ShouldReturnEmpty() {
        // O bucket não existe
        Optional<Bucket> bucket = bucketStore.getBucket(TEST_KEY);
        
        assertFalse(bucket.isPresent(), "Deve retornar Optional vazio quando o bucket não existe");
    }

    @Test
    void testGetBucketOptional_WhenBucketExists_ShouldReturnBucket() {
        // Cria o bucket primeiro
        Bucket originalBucket = bucketStore.getBucket(TEST_KEY, config);
        
        // Obtém o bucket novamente
        Optional<Bucket> bucket = bucketStore.getBucket(TEST_KEY);
        
        assertTrue(bucket.isPresent(), "Deve retornar Optional com bucket quando ele existe");
        assertSame(originalBucket, bucket.get(), "Deve retornar o mesmo bucket original");
    }

    @Test
    void testGetBucketAsync_ShouldReturnFuture() throws ExecutionException, InterruptedException {
        // Obtenção assíncrona
        CompletableFuture<Bucket> future = bucketStore.getBucketAsync(TEST_KEY, config);
        
        assertNotNull(future, "Deve retornar um CompletableFuture");
        Bucket bucket = future.get(); // Aguarda a conclusão
        
        assertNotNull(bucket, "O future deve completar com um bucket");
        assertEquals(config, bucket.getConfig(), "O bucket deve usar a configuração fornecida");
    }

    @Test
    void testSaveBucket_ShouldStoreNewBucket() {
        // Cria um bucket
        Bucket bucket = new Bucket(config);
        
        // Consome alguns tokens
        bucket.tryConsume(5);
        
        // Salva o bucket
        bucketStore.saveBucket(TEST_KEY, bucket);
        
        // Tenta recuperá-lo
        Optional<Bucket> retrieved = bucketStore.getBucket(TEST_KEY);
        
        assertTrue(retrieved.isPresent(), "Deve ter guardado o bucket");
        assertSame(bucket, retrieved.get(), "Deve recuperar o mesmo bucket");
        assertEquals(5, retrieved.get().getAvailableTokens(), "O bucket deve manter seu estado");
    }

    @Test
    void testSaveBucketAsync_ShouldStoreNewBucket() throws ExecutionException, InterruptedException {
        // Cria um bucket
        Bucket bucket = new Bucket(config);
        
        // Consome alguns tokens
        bucket.tryConsume(5);
        
        // Salva o bucket assincronamente
        CompletableFuture<Void> future = bucketStore.saveBucketAsync(TEST_KEY, bucket);
        
        assertNotNull(future, "Deve retornar um CompletableFuture");
        future.get(); // Aguarda a conclusão
        
        // Tenta recuperá-lo
        Optional<Bucket> retrieved = bucketStore.getBucket(TEST_KEY);
        
        assertTrue(retrieved.isPresent(), "Deve ter guardado o bucket");
        assertSame(bucket, retrieved.get(), "Deve recuperar o mesmo bucket");
    }

    @Test
    void testRemoveBucket_WhenBucketExists_ShouldRemoveAndReturnTrue() {
        // Cria o bucket primeiro
        bucketStore.getBucket(TEST_KEY, config);
        
        // Verifica que está armazenado
        assertTrue(bucketStore.getBucket(TEST_KEY).isPresent(), "O bucket deve existir antes de remover");
        
        // Remove o bucket
        boolean result = bucketStore.removeBucket(TEST_KEY);
        
        assertTrue(result, "Deve retornar true ao remover bucket existente");
        assertFalse(bucketStore.getBucket(TEST_KEY).isPresent(), "O bucket não deve existir após remover");
    }

    @Test
    void testRemoveBucket_WhenBucketDoesNotExist_ShouldReturnFalse() {
        // Tenta remover bucket que não existe
        boolean result = bucketStore.removeBucket(TEST_KEY);
        
        assertFalse(result, "Deve retornar false ao tentar remover bucket que não existe");
    }

    @Test
    void testRemoveBucketAsync_ShouldRemoveAndReturnFuture() throws ExecutionException, InterruptedException {
        // Cria o bucket primeiro
        bucketStore.getBucket(TEST_KEY, config);
        
        // Remove o bucket assincronamente
        CompletableFuture<Boolean> future = bucketStore.removeBucketAsync(TEST_KEY);
        
        assertNotNull(future, "Deve retornar um CompletableFuture");
        boolean result = future.get(); // Aguarda a conclusão
        
        assertTrue(result, "Future deve completar com true ao remover bucket existente");
        assertFalse(bucketStore.getBucket(TEST_KEY).isPresent(), "O bucket não deve existir após remover");
    }

    @Test
    void testClear_ShouldRemoveAllBuckets() {
        // Cria alguns buckets
        bucketStore.getBucket("key1", config);
        bucketStore.getBucket("key2", config);
        bucketStore.getBucket("key3", config);
        
        // Verifica que foram criados
        assertEquals(3, bucketStore.size(), "Deve haver 3 buckets armazenados");
        
        // Limpa o armazenamento
        bucketStore.clear();
        
        // Verifica que foram removidos
        assertEquals(0, bucketStore.size(), "Não deve haver buckets após clear()");
        assertFalse(bucketStore.getBucket("key1").isPresent(), "O bucket 'key1' não deve existir após clear()");
        assertFalse(bucketStore.getBucket("key2").isPresent(), "O bucket 'key2' não deve existir após clear()");
        assertFalse(bucketStore.getBucket("key3").isPresent(), "O bucket 'key3' não deve existir após clear()");
    }

    @Test
    void testSize_ShouldReturnCorrectCount() {
        assertEquals(0, bucketStore.size(), "Inicialmente não deve haver buckets");
        
        // Adiciona buckets
        bucketStore.getBucket("key1", config);
        assertEquals(1, bucketStore.size(), "Deve haver 1 bucket");
        
        bucketStore.getBucket("key2", config);
        assertEquals(2, bucketStore.size(), "Deve haver 2 buckets");
        
        // Remove um bucket
        bucketStore.removeBucket("key1");
        assertEquals(1, bucketStore.size(), "Deve haver 1 bucket após remover");
        
        // Limpa todos
        bucketStore.clear();
        assertEquals(0, bucketStore.size(), "Não deve haver buckets após clear()");
    }
} 