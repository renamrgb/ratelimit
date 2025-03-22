package com.ratelimit.core.exception;

/**
 * Exceção lançada quando um limite de taxa é excedido.
 */
public class RateLimitExceededException extends RuntimeException {
    
    private final String resourceId;
    private final long waitTimeMillis;
    
    /**
     * Cria uma nova exceção de limite de taxa excedido.
     * 
     * @param message mensagem de erro
     */
    public RateLimitExceededException(String message) {
        this(message, null, -1);
    }
    
    /**
     * Cria uma nova exceção de limite de taxa excedido.
     * 
     * @param message mensagem de erro
     * @param resourceId identificador do recurso
     */
    public RateLimitExceededException(String message, String resourceId) {
        this(message, resourceId, -1);
    }
    
    /**
     * Cria uma nova exceção de limite de taxa excedido com tempo de espera.
     * 
     * @param message mensagem de erro
     * @param resourceId identificador do recurso
     * @param waitTimeMillis tempo de espera em milissegundos até que o limite seja liberado
     */
    public RateLimitExceededException(String message, String resourceId, long waitTimeMillis) {
        super(message);
        this.resourceId = resourceId;
        this.waitTimeMillis = waitTimeMillis;
    }
    
    /**
     * @return o identificador do recurso que teve o limite excedido, ou null se não disponível
     */
    public String getResourceId() {
        return resourceId;
    }
    
    /**
     * @return o tempo estimado de espera em milissegundos até que o limite seja liberado,
     *         ou -1 se não for possível estimar
     */
    public long getWaitTimeMillis() {
        return waitTimeMillis;
    }
} 