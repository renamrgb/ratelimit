package com.ratelimit.adapters.redis;

import com.ratelimit.adapters.redis.RedisBucketStore.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Implementação da interface RedisClient baseada na biblioteca Lettuce.
 */
public class LettuceRedisClient implements RedisClient {
    
    private final StatefulRedisConnection<String, byte[]> connection;
    private final RedisAsyncCommands<String, byte[]> asyncCommands;
    
    /**
     * Cria um novo cliente Redis baseado em Lettuce.
     * 
     * @param connection conexão Lettuce para Redis
     */
    public LettuceRedisClient(StatefulRedisConnection<String, byte[]> connection) {
        this.connection = Objects.requireNonNull(connection, "Conexão Redis não pode ser nula");
        this.asyncCommands = connection.async();
    }
    
    @Override
    public byte[] get(String key) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        return connection.sync().get(key);
    }
    
    @Override
    public void set(String key, byte[] value, long expirySeconds) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        Objects.requireNonNull(value, "Valor não pode ser nulo");
        
        if (expirySeconds > 0) {
            connection.sync().setex(key, expirySeconds, value);
        } else {
            connection.sync().set(key, value);
        }
    }
    
    @Override
    public long delete(String key) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        return connection.sync().del(key);
    }
    
    @Override
    public CompletableFuture<byte[]> getAsync(String key) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        return asyncCommands.get(key).toCompletableFuture();
    }
    
    @Override
    public CompletableFuture<String> setAsync(String key, byte[] value, long expirySeconds) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        Objects.requireNonNull(value, "Valor não pode ser nulo");
        
        if (expirySeconds > 0) {
            return asyncCommands.setex(key, expirySeconds, value).toCompletableFuture();
        } else {
            return asyncCommands.set(key, value).toCompletableFuture();
        }
    }
    
    @Override
    public CompletableFuture<Long> deleteAsync(String key) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        return asyncCommands.del(key).toCompletableFuture();
    }
    
    /**
     * Fecha a conexão Redis quando não for mais necessária.
     * É importante chamar este método para liberar recursos.
     */
    public void close() {
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }
    
    /**
     * Fábrica para criar um cliente Redis baseado em Lettuce.
     */
    public static class Builder {
        private StatefulRedisConnection<String, byte[]> connection;
        
        /**
         * Define a conexão Redis.
         * 
         * @param connection conexão Lettuce para Redis
         * @return o builder para encadeamento
         */
        public Builder withConnection(StatefulRedisConnection<String, byte[]> connection) {
            this.connection = Objects.requireNonNull(connection, "Conexão Redis não pode ser nula");
            return this;
        }
        
        /**
         * Constrói um novo cliente Redis baseado em Lettuce.
         * 
         * @return uma nova instância de LettuceRedisClient
         */
        public LettuceRedisClient build() {
            if (connection == null) {
                throw new IllegalStateException("A conexão Redis deve ser configurada");
            }
            return new LettuceRedisClient(connection);
        }
    }
    
    /**
     * Cria um novo builder para configurar um cliente Redis baseado em Lettuce.
     * 
     * @return uma nova instância de Builder
     */
    public static Builder builder() {
        return new Builder();
    }
} 