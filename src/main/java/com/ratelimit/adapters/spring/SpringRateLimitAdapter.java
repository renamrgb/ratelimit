package com.ratelimit.adapters.spring;

import com.ratelimit.core.RateLimiter;
import com.ratelimit.core.exception.RateLimitExceededException;
import com.ratelimit.core.model.RateLimitConfig;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador que integra a SDK de rate limiting com o Spring Framework
 * usando AspectJ para interceptar métodos anotados.
 */
@Aspect
@Order(0) // Alta prioridade
public class SpringRateLimitAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(SpringRateLimitAdapter.class);
    
    private final RateLimiter rateLimiter;
    
    /**
     * Cria um novo adaptador para Spring.
     * 
     * @param rateLimiter o limitador de taxa a ser usado
     */
    public SpringRateLimitAdapter(RateLimiter rateLimiter) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "RateLimiter não pode ser nulo");
    }
    
    /**
     * Intercepta métodos anotados com @RateLimit e aplica o rate limiting.
     * 
     * @param joinPoint ponto de junção AspectJ
     * @param rateLimit anotação de rate limit
     * @return o resultado do método original ou uma exceção se o limite for excedido
     * @throws Throwable se ocorrer um erro na execução do método ou se o limite for excedido
     */
    @Around("@annotation(rateLimit)")
    public Object limitRate(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String resourceId = generateResourceId(joinPoint, rateLimit);
        
        // Constrói a configuração a partir da anotação
        RateLimitConfig config = buildConfig(rateLimit);
        
        // Configura o RateLimiter com a config específica, se possível
        
        // Verifica se pode executar dentro do limite de taxa
        if (rateLimiter.tryAcquire(resourceId)) {
            // Permite a execução
            return joinPoint.proceed();
        } else {
            // Limite excedido, lança exceção
            long retryAfterMillis = estimateWaitTimeMillis(resourceId, rateLimit);
            
            logger.warn("Limite de taxa excedido para recurso: {}", resourceId);
            
            throw new RateLimitExceededException(
                    "Limite de taxa excedido para recurso: " + resourceId,
                    resourceId,
                    retryAfterMillis);
        }
    }
    
    /**
     * Gera um identificador para o recurso a partir do joinpoint e da anotação.
     * 
     * @param joinPoint ponto de junção AspectJ
     * @param rateLimit anotação de rate limit
     * @return identificador único para o recurso
     */
    private String generateResourceId(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        String key = rateLimit.key();
        if (key.isEmpty()) {
            // Se a chave não for especificada, usa o nome da classe + método como chave
            key = method.getDeclaringClass().getName() + "." + method.getName();
        }
        
        return key;
    }
    
    /**
     * Constrói uma configuração de rate limit a partir da anotação.
     * 
     * @param rateLimit anotação de rate limit
     * @return configuração de rate limit
     */
    private RateLimitConfig buildConfig(RateLimit rateLimit) {
        RateLimitConfig.RefillStrategy strategy = rateLimit.greedy() ?
                RateLimitConfig.RefillStrategy.GREEDY :
                RateLimitConfig.RefillStrategy.GRADUAL;
        
        return RateLimitConfig.builder()
                .tokensPerPeriod(rateLimit.limit())
                .period(Duration.ofSeconds(rateLimit.duration()))
                .refillStrategy(strategy)
                .build();
    }
    
    /**
     * Estima o tempo de espera até que o recurso esteja disponível novamente.
     * 
     * @param resourceId identificador do recurso
     * @param rateLimit anotação de rate limit
     * @return tempo estimado de espera em milissegundos
     */
    private long estimateWaitTimeMillis(String resourceId, RateLimit rateLimit) {
        // Implementação simplificada: assume um tempo baseado na duração
        return TimeUnit.SECONDS.toMillis(rateLimit.duration()) / rateLimit.limit();
    }
} 