package com.example.ratelimit.sdk.annotation;

import java.lang.annotation.*;

/**
 * Configuração de retry para operações de rate limit.
 * <p>
 * Essa anotação define as regras para tentativas adicionais quando uma operação falha
 * ao ser executada sob um controle de rate limit. Permite configurar o número máximo
 * de tentativas, tipos de exceções que devem ser retentadas, e o comportamento de backoff.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Retry {

    /**
     * Define o número máximo de tentativas permitidas.
     * <p>
     * Um valor de 1 significa que a operação será tentada apenas uma vez (não haverá retry).
     * Um valor maior que 1 indica o total de tentativas, incluindo a tentativa inicial.
     * </p>
     *
     * @return número máximo de tentativas (padrão: 1, sem retry).
     */
    int maxAttempts() default 1;

    /**
     * Define as classes de exceção que habilitam tentativas adicionais.
     * <p>
     * Quando uma operação lança uma exceção desses tipos ou subtipos, o mecanismo de retry
     * será acionado para tentar a operação novamente, respeitando as políticas de backoff.
     * </p>
     *
     * @return array de classes de exceção que ativam o retry (padrão: vazio).
     */
    Class<? extends Throwable>[] include() default {};

    /**
     * Define as classes de exceção que desabilitam tentativas adicionais.
     * <p>
     * Mesmo que a exceção lançada esteja na lista de inclusão ({@link #include()}), se ela
     * também estiver na lista de exclusão, o retry não será acionado.
     * </p>
     *
     * @return array de classes de exceção que inibem o retry (padrão: vazio).
     */
    Class<? extends Throwable>[] exclude() default {};

    /**
     * Define a configuração de backoff a ser aplicada entre as tentativas.
     * <p>
     * A configuração de backoff determina o intervalo de espera entre as tentativas,
     * permitindo implementar estratégias de backoff como crescimento exponencial.
     * </p>
     *
     * @return configuração de backoff para intervalos entre tentativas.
     */
    BackoffRetry backoff() default @BackoffRetry;

    /**
     * Define o nome do método de fallback a ser invocado se todas as tentativas falharem.
     * <p>
     * O método de fallback deve ser definido na mesma classe do método anotado com {@link RateLimit},
     * e deve ter a mesma assinatura, acrescida de um parâmetro adicional do tipo {@link Throwable}
     * como último argumento, que conterá a exceção que causou a falha.
     * </p>
     *
     * @return nome do método de fallback (padrão: string vazia, sem fallback).
     */
    String fallbackMethod() default "";
    
    /**
     * Define uma descrição para o propósito deste retry, útil para logs e monitoramento.
     * <p>
     * Esta descrição ajuda a entender o motivo pelo qual o retry está sendo aplicado
     * e pode ser usada em ferramentas de análise e monitoramento.
     * </p>
     * 
     * @return uma descrição explicativa para o retry (padrão: string vazia).
     */
    String description() default "";
    
    /**
     * Define se o retry deve ser registrado em métricas quando ativado.
     * <p>
     * Quando habilitado e o suporte a métricas estiver disponível, cada tentativa
     * será registrada para fins de monitoramento.
     * </p>
     * 
     * @return true para habilitar métricas de retry, false caso contrário (padrão: true).
     */
    boolean metricsEnabled() default true;
}
