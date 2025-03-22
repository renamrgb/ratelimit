package com.ratelimit.adapters.redis;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LettuceRedisClientTest {

    @Mock
    private StatefulRedisConnection<String, byte[]> connection;
    
    @Mock
    private RedisCommands<String, byte[]> syncCommands;
    
    @Mock
    private RedisAsyncCommands<String, byte[]> asyncCommands;
    
    private LettuceRedisClient redisClient;
    
    private final String testKey = "test-key";
    private final byte[] testValue = "test-value".getBytes();
    private final long testExpiry = 60;

    @BeforeEach
    void setUp() {
        when(connection.sync()).thenReturn(syncCommands);
        when(connection.async()).thenReturn(asyncCommands);
        
        redisClient = new LettuceRedisClient(connection);
    }

    @Test
    void testGet_ShouldDelegateToSyncCommands() {
        when(syncCommands.get(testKey)).thenReturn(testValue);
        
        byte[] result = redisClient.get(testKey);
        
        assertArrayEquals(testValue, result);
        verify(syncCommands).get(testKey);
    }

    @Test
    void testSetWithExpiry_ShouldUseSetex() {
        redisClient.set(testKey, testValue, testExpiry);
        
        verify(syncCommands).setex(testKey, testExpiry, testValue);
    }

    @Test
    void testSetWithoutExpiry_ShouldUseSet() {
        redisClient.set(testKey, testValue, 0);
        
        verify(syncCommands).set(testKey, testValue);
    }

    @Test
    void testDelete_ShouldDelegateToSyncCommands() {
        when(syncCommands.del(testKey)).thenReturn(1L);
        
        long result = redisClient.delete(testKey);
        
        assertEquals(1L, result);
        verify(syncCommands).del(testKey);
    }

    @Test
    void testGetAsync_ShouldDelegateToAsyncCommands() {
        CompletableFuture<byte[]> future = CompletableFuture.completedFuture(testValue);
        when(asyncCommands.get(testKey)).thenReturn(io.lettuce.core.RedisFuture.completedFuture(testValue));
        
        CompletableFuture<byte[]> result = redisClient.getAsync(testKey);
        
        assertNotNull(result);
        verify(asyncCommands).get(testKey);
    }

    @Test
    void testSetAsyncWithExpiry_ShouldUseSetex() {
        when(asyncCommands.setex(testKey, testExpiry, testValue))
                .thenReturn(io.lettuce.core.RedisFuture.completedFuture("OK"));
        
        CompletableFuture<String> result = redisClient.setAsync(testKey, testValue, testExpiry);
        
        assertNotNull(result);
        verify(asyncCommands).setex(testKey, testExpiry, testValue);
    }

    @Test
    void testSetAsyncWithoutExpiry_ShouldUseSet() {
        when(asyncCommands.set(testKey, testValue))
                .thenReturn(io.lettuce.core.RedisFuture.completedFuture("OK"));
        
        CompletableFuture<String> result = redisClient.setAsync(testKey, testValue, 0);
        
        assertNotNull(result);
        verify(asyncCommands).set(testKey, testValue);
    }

    @Test
    void testDeleteAsync_ShouldDelegateToAsyncCommands() {
        when(asyncCommands.del(testKey))
                .thenReturn(io.lettuce.core.RedisFuture.completedFuture(1L));
        
        CompletableFuture<Long> result = redisClient.deleteAsync(testKey);
        
        assertNotNull(result);
        verify(asyncCommands).del(testKey);
    }

    @Test
    void testClose_WithNullConnection_ShouldNotFail() {
        redisClient = new LettuceRedisClient(null);
        
        // Não deve lançar exceção
        assertDoesNotThrow(() -> redisClient.close());
    }

    @Test
    void testClose_WithClosedConnection_ShouldNotFail() {
        when(connection.isOpen()).thenReturn(false);
        
        // Não deve lançar exceção
        assertDoesNotThrow(() -> redisClient.close());
        
        verify(connection).isOpen();
        verify(connection, never()).close();
    }

    @Test
    void testClose_WithOpenConnection_ShouldClose() {
        when(connection.isOpen()).thenReturn(true);
        
        redisClient.close();
        
        verify(connection).isOpen();
        verify(connection).close();
    }

    @Test
    void testBuilder_WithoutConnection_ShouldThrowException() {
        assertThrows(IllegalStateException.class, () -> 
                LettuceRedisClient.builder().build());
    }

    @Test
    void testBuilder_WithNullConnection_ShouldThrowException() {
        assertThrows(NullPointerException.class, () -> 
                LettuceRedisClient.builder().withConnection(null));
    }

    @Test
    void testBuilder_WithConnection_ShouldBuildClient() {
        LettuceRedisClient client = LettuceRedisClient.builder()
                .withConnection(connection)
                .build();
        
        assertNotNull(client);
    }
} 