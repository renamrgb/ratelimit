package com.ratelimit.adapters.redis;

import com.ratelimit.core.model.Bucket;
import com.ratelimit.core.model.RateLimitConfig;
import com.ratelimit.spi.storage.BucketStore;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação de BucketStore que utiliza Redis para armazenar buckets.
 * Permite o rate limiting distribuído entre múltiplas instâncias.
 */
public class RedisBucketStore implements BucketStore {
    
    private final RedisClient redisClient;
    private final String keyPrefix;
    private final long cacheExpiryMillis;
    
    // Cache local para reduzir o overhead de rede
    private final Map<String, Bucket> localCache = new ConcurrentHashMap<>();
    
    /**
     * Cria um novo armazenamento Redis com as configurações padrão.
     * 
     * @param redisClient cliente Redis a ser usado
     */
    public RedisBucketStore(RedisClient redisClient) {
        this(redisClient, "ratelimit:", 60 * 1000); // 1 minuto de tempo de expiração padrão
    }
    
    /**
     * Cria um novo armazenamento Redis com as configurações especificadas.
     * 
     * @param redisClient cliente Redis a ser usado
     * @param keyPrefix prefixo para as chaves Redis
     * @param cacheExpiryMillis tempo de expiração do cache local em milissegundos
     */
    public RedisBucketStore(RedisClient redisClient, String keyPrefix, long cacheExpiryMillis) {
        this.redisClient = Objects.requireNonNull(redisClient, "Cliente Redis não pode ser nulo");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "Prefixo de chave não pode ser nulo");
        
        if (cacheExpiryMillis <= 0) {
            throw new IllegalArgumentException("Tempo de expiração deve ser maior que zero");
        }
        this.cacheExpiryMillis = cacheExpiryMillis;
    }
    
    @Override
    public Bucket getBucket(String key, RateLimitConfig config) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        Objects.requireNonNull(config, "Configuração não pode ser nula");
        
        // Tenta obter do cache local primeiro
        Bucket cachedBucket = localCache.get(key);
        if (cachedBucket != null) {
            return cachedBucket;
        }
        
        // Tenta obter do Redis
        String redisKey = keyPrefix + key;
        
        try {
            byte[] serializedBucket = redisClient.get(redisKey);
            
            if (serializedBucket != null) {
                // Desserializa o bucket do Redis
                Bucket bucket = deserializeBucket(serializedBucket);
                // Atualiza o cache local
                localCache.put(key, bucket);
                return bucket;
            }
        } catch (Exception e) {
            // Em caso de erro, loga e cria um novo bucket como fallback
            System.err.println("Erro ao obter bucket do Redis: " + e.getMessage());
        }
        
        // Se não encontrou no Redis ou ocorreu erro, cria um novo bucket
        Bucket newBucket = new Bucket(config);
        saveBucket(key, newBucket);
        
        return newBucket;
    }
    
    @Override
    public Optional<Bucket> getBucket(String key) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        
        // Tenta obter do cache local primeiro
        Bucket cachedBucket = localCache.get(key);
        if (cachedBucket != null) {
            return Optional.of(cachedBucket);
        }
        
        // Tenta obter do Redis
        String redisKey = keyPrefix + key;
        
        try {
            byte[] serializedBucket = redisClient.get(redisKey);
            
            if (serializedBucket != null) {
                // Desserializa o bucket do Redis
                Bucket bucket = deserializeBucket(serializedBucket);
                // Atualiza o cache local
                localCache.put(key, bucket);
                return Optional.of(bucket);
            }
        } catch (Exception e) {
            // Em caso de erro, loga e retorna vazio
            System.err.println("Erro ao obter bucket do Redis: " + e.getMessage());
        }
        
        return Optional.empty();
    }
    
    @Override
    public CompletableFuture<Bucket> getBucketAsync(String key, RateLimitConfig config) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        Objects.requireNonNull(config, "Configuração não pode ser nula");
        
        // Tenta obter do cache local primeiro
        Bucket cachedBucket = localCache.get(key);
        if (cachedBucket != null) {
            return CompletableFuture.completedFuture(cachedBucket);
        }
        
        String redisKey = keyPrefix + key;
        
        CompletableFuture<Bucket> future = new CompletableFuture<>();
        
        redisClient.getAsync(redisKey)
            .thenAccept(serializedBucket -> {
                try {
                    Bucket bucket;
                    
                    if (serializedBucket != null) {
                        // Desserializa o bucket do Redis
                        bucket = deserializeBucket(serializedBucket);
                    } else {
                        // Cria um novo bucket se não existir
                        bucket = new Bucket(config);
                        // Salva o novo bucket de forma assíncrona
                        saveBucketAsync(key, bucket);
                    }
                    
                    // Atualiza o cache local
                    localCache.put(key, bucket);
                    future.complete(bucket);
                } catch (Exception e) {
                    // Em caso de erro, cria um novo bucket como fallback
                    Bucket bucket = new Bucket(config);
                    // Tenta salvar o novo bucket de forma assíncrona
                    saveBucketAsync(key, bucket);
                    future.complete(bucket);
                }
            })
            .exceptionally(e -> {
                // Em caso de erro, cria um novo bucket como fallback
                Bucket bucket = new Bucket(config);
                // Tenta salvar o novo bucket de forma assíncrona
                saveBucketAsync(key, bucket);
                future.complete(bucket);
                return null;
            });
        
        return future;
    }
    
    @Override
    public void saveBucket(String key, Bucket bucket) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        Objects.requireNonNull(bucket, "Bucket não pode ser nulo");
        
        // Atualiza o cache local
        localCache.put(key, bucket);
        
        // Serializa e salva no Redis
        String redisKey = keyPrefix + key;
        byte[] serializedBucket = serializeBucket(bucket);
        
        try {
            // Define o tempo de expiração baseado na configuração do bucket
            long expirySeconds = bucket.getConfig().getPeriod().getSeconds() * 2;
            // Mínimo de 1 minuto
            expirySeconds = Math.max(expirySeconds, 60);
            
            redisClient.set(redisKey, serializedBucket, expirySeconds);
        } catch (Exception e) {
            // Loga o erro, mas não propaga para não interromper a aplicação
            System.err.println("Erro ao salvar bucket no Redis: " + e.getMessage());
        }
    }
    
    @Override
    public CompletableFuture<Void> saveBucketAsync(String key, Bucket bucket) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        Objects.requireNonNull(bucket, "Bucket não pode ser nulo");
        
        // Atualiza o cache local
        localCache.put(key, bucket);
        
        // Serializa e salva no Redis
        String redisKey = keyPrefix + key;
        byte[] serializedBucket = serializeBucket(bucket);
        
        // Define o tempo de expiração baseado na configuração do bucket
        long expirySeconds = bucket.getConfig().getPeriod().getSeconds() * 2;
        // Mínimo de 1 minuto
        expirySeconds = Math.max(expirySeconds, 60);
        
        return redisClient.setAsync(redisKey, serializedBucket, expirySeconds)
            .thenApply(result -> null)
            .exceptionally(e -> {
                // Loga o erro, mas completa o future normalmente
                System.err.println("Erro ao salvar bucket no Redis de forma assíncrona: " + e.getMessage());
                return null;
            });
    }
    
    @Override
    public boolean removeBucket(String key) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        
        // Remove do cache local
        localCache.remove(key);
        
        // Remove do Redis
        String redisKey = keyPrefix + key;
        
        try {
            return redisClient.delete(redisKey) > 0;
        } catch (Exception e) {
            // Loga o erro e retorna false
            System.err.println("Erro ao remover bucket do Redis: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public CompletableFuture<Boolean> removeBucketAsync(String key) {
        Objects.requireNonNull(key, "Chave não pode ser nula");
        
        // Remove do cache local
        localCache.remove(key);
        
        // Remove do Redis
        String redisKey = keyPrefix + key;
        
        return redisClient.deleteAsync(redisKey)
            .thenApply(result -> result > 0)
            .exceptionally(e -> {
                // Loga o erro e retorna false
                System.err.println("Erro ao remover bucket do Redis de forma assíncrona: " + e.getMessage());
                return false;
            });
    }
    
    /**
     * Limpa o cache local.
     */
    public void clearLocalCache() {
        localCache.clear();
    }
    
    // Métodos privados para serialização e desserialização
    
    private byte[] serializeBucket(Bucket bucket) {
        // Implementação simplificada - na prática, usaria um mecanismo de serialização
        // como Jackson, Gson, Protocol Buffers, etc.
        return new byte[0]; // Placeholder
    }
    
    private Bucket deserializeBucket(byte[] data) {
        // Implementação simplificada - na prática, usaria um mecanismo de desserialização
        // como Jackson, Gson, Protocol Buffers, etc.
        return null; // Placeholder
    }
    
    /**
     * Interface que abstrai as operações básicas do Redis.
     * Em uma implementação real, seria integrada com uma biblioteca Redis como Lettuce ou Jedis.
     */
    public interface RedisClient {
        byte[] get(String key);
        void set(String key, byte[] value, long expirySeconds);
        long delete(String key);
        
        CompletableFuture<byte[]> getAsync(String key);
        CompletableFuture<String> setAsync(String key, byte[] value, long expirySeconds);
        CompletableFuture<Long> deleteAsync(String key);
    }
} 