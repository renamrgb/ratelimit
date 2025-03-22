package com.ratelimit.adapters.redis;

import com.ratelimit.core.model.Bucket;
import com.ratelimit.core.model.RateLimitConfig;
import com.ratelimit.spi.BucketStore;
import com.ratelimit.spi.RedisClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisBucketStoreTest {

    @Mock
    private RedisClient redisClient;
    
    private RedisBucketStore bucketStore;
    
    private final String testKey = "test-key";
    private final RateLimitConfig testConfig = RateLimitConfig.builder()
            .tokensPerPeriod(10)
            .period(Duration.ofSeconds(1))
            .build();
    
    private byte[] serializedBucket;

    @BeforeEach
    void setUp() {
        bucketStore = new RedisBucketStore(redisClient);
        
        // Criar um bucket para serialização
        Bucket bucket = Bucket.create(testConfig);
        serializedBucket = bucketStore.serialize(bucket);
    }

    @Test
    void testGetBucket_WhenNotExists_ShouldCreateNew() {
        when(redisClient.get(anyString())).thenReturn(null);
        
        Bucket result = bucketStore.getBucket(testKey, testConfig);
        
        assertNotNull(result);
        assertEquals(testConfig.getTokensPerPeriod(), result.getAvailableTokens());
        
        verify(redisClient).get(eq(testKey));
        
        // Capturar e verificar a chamada para set
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisClient).set(eq(testKey), dataCaptor.capture(), anyLong());
        
        byte[] capturedData = dataCaptor.getValue();
        assertNotNull(capturedData);
        assertTrue(capturedData.length > 0);
    }

    @Test
    void testGetBucket_WhenExists_ShouldDeserialize() {
        when(redisClient.get(testKey)).thenReturn(serializedBucket);
        
        Bucket result = bucketStore.getBucket(testKey, testConfig);
        
        assertNotNull(result);
        assertEquals(testConfig.getTokensPerPeriod(), result.getAvailableTokens());
        
        verify(redisClient).get(eq(testKey));
        verify(redisClient, never()).set(eq(testKey), any(byte[].class), anyLong());
    }

    @Test
    void testUpdateBucket_ShouldSerializeAndStore() {
        Bucket bucket = Bucket.create(testConfig);
        bucket.tryConsume(5); // Consumir alguns tokens
        
        bucketStore.updateBucket(testKey, bucket);
        
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisClient).set(eq(testKey), dataCaptor.capture(), anyLong());
        
        byte[] capturedData = dataCaptor.getValue();
        assertNotNull(capturedData);
        assertTrue(capturedData.length > 0);
        
        // Deserializar e verificar que mantém o estado
        when(redisClient.get(testKey)).thenReturn(capturedData);
        Bucket retrievedBucket = bucketStore.getBucket(testKey, testConfig);
        assertEquals(5, retrievedBucket.getAvailableTokens());
    }

    @Test
    void testGetBucketAsync_WhenNotExists_ShouldCreateNew() {
        when(redisClient.getAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(redisClient.setAsync(anyString(), any(byte[].class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture("OK"));
        
        CompletableFuture<Bucket> future = bucketStore.getBucketAsync(testKey, testConfig);
        
        assertNotNull(future);
        Bucket result = future.join();
        
        assertNotNull(result);
        assertEquals(testConfig.getTokensPerPeriod(), result.getAvailableTokens());
        
        verify(redisClient).getAsync(eq(testKey));
        verify(redisClient).setAsync(eq(testKey), any(byte[].class), anyLong());
    }

    @Test
    void testGetBucketAsync_WhenExists_ShouldDeserialize() {
        when(redisClient.getAsync(testKey))
                .thenReturn(CompletableFuture.completedFuture(serializedBucket));
        
        CompletableFuture<Bucket> future = bucketStore.getBucketAsync(testKey, testConfig);
        
        assertNotNull(future);
        Bucket result = future.join();
        
        assertNotNull(result);
        assertEquals(testConfig.getTokensPerPeriod(), result.getAvailableTokens());
        
        verify(redisClient).getAsync(eq(testKey));
        verify(redisClient, never()).setAsync(eq(testKey), any(byte[].class), anyLong());
    }

    @Test
    void testUpdateBucketAsync_ShouldSerializeAndStore() {
        Bucket bucket = Bucket.create(testConfig);
        bucket.tryConsume(5); // Consumir alguns tokens
        
        when(redisClient.setAsync(anyString(), any(byte[].class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture("OK"));
        
        CompletableFuture<Void> future = bucketStore.updateBucketAsync(testKey, bucket);
        
        assertNotNull(future);
        assertDoesNotThrow(() -> future.join());
        
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisClient).setAsync(eq(testKey), dataCaptor.capture(), anyLong());
        
        byte[] capturedData = dataCaptor.getValue();
        assertNotNull(capturedData);
    }

    @Test
    void testSerialization_DeserializationRoundTrip() {
        Bucket bucket = Bucket.create(testConfig);
        bucket.tryConsume(3); // Consumir 3 tokens
        
        byte[] serialized = bucketStore.serialize(bucket);
        Bucket deserialized = bucketStore.deserialize(serialized, testConfig);
        
        assertNotNull(deserialized);
        assertEquals(7, deserialized.getAvailableTokens()); // 10 - 3 = 7
    }

    @Test
    void testDeserialize_WithCorruptedData_ShouldReturnNewBucket() {
        byte[] corruptedData = new byte[] {1, 2, 3}; // Dados inválidos
        
        Bucket result = bucketStore.deserialize(corruptedData, testConfig);
        
        assertNotNull(result);
        assertEquals(testConfig.getTokensPerPeriod(), result.getAvailableTokens());
    }

    @Test
    void testDeserialize_WithNullData_ShouldReturnNewBucket() {
        Bucket result = bucketStore.deserialize(null, testConfig);
        
        assertNotNull(result);
        assertEquals(testConfig.getTokensPerPeriod(), result.getAvailableTokens());
    }
} 