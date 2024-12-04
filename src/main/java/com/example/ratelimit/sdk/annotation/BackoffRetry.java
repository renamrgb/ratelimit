package com.example.ratelimit.sdk.annotation;

import java.lang.annotation.*;

/**
 * Configuração de backoff para estratégias de retry.
 * <p>
 * Essa anotação é usada como dependência da anotação {@link Retry}, permitindo configurar
 * o comportamento de espera entre as tentativas de retry. O backoff pode ser configurado
 * para ter um tempo inicial, um tempo máximo e um multiplicador para cálculo de backoff
 * exponencial.
 * </p>
 * <p>
 * Deve ser usada em conjunto com {@link Retry}, não sendo aplicável de forma independente.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface BackoffRetry {

    /**
     * Define o tempo de espera inicial entre as tentativas de retry.
     * <p>
     * Esse valor será usado como base para o cálculo do backoff.
     * </p>
     *
     * @return tempo de espera inicial em milissegundos (padrão: 1000 ms).
     */
    long delay() default 1000L;

    /**
     * Define o tempo máximo de espera entre as tentativas de retry.
     * <p>
     * Caso configurado como 0, o tempo máximo será definido automaticamente
     * com base no multiplicador e no número de tentativas.
     * </p>
     *
     * @return tempo máximo de espera em milissegundos (padrão: 0, sem limite explícito).
     */
    long maxDelay() default 0L;

    /**
     * Define o multiplicador usado para calcular o backoff exponencial.
     * <p>
     * O multiplicador é aplicado ao tempo de espera inicial após cada tentativa de retry,
     * resultando em um aumento exponencial do tempo de espera.
     * </p>
     *
     * @return multiplicador para cálculo de backoff exponencial (padrão: 1.0).
     */
    double multiplier() default 1.0;
}
