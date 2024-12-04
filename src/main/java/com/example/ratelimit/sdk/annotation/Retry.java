package com.example.ratelimit.sdk.annotation;

import java.lang.annotation.*;

/**
 * Configuração de lógica de retry (tentativas) para métodos anotados com {@link RateLimit}.
 * <p>
 * Essa anotação é projetada para ser usada exclusivamente como dependência da anotação
 * {@link RateLimit}, permitindo configurar comportamentos adicionais de retry.
 * Ela não deve ser utilizada de forma independente.
 * </p>
 * <p>
 * Permite configurar o número máximo de tentativas, quais exceções devem acionar o retry,
 * quais devem ser excluídas, e o comportamento de backoff exponencial entre as tentativas.
 * Também é possível configurar um método de fallback a ser executado caso todas as tentativas falhem.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Retry {

    /**
     * Define o número máximo de tentativas permitidas para o método anotado.
     *
     * @return número máximo de tentativas (padrão: 3).
     */
    int maxAttempts() default 3;

    /**
     * Define as exceções que devem acionar o retry.
     * <p>
     * Se não configurado, o retry será aplicado para todas as exceções,
     * exceto as explicitamente listadas em {@link #exclude()}.
     * </p>
     *
     * @return array de classes de exceções que devem acionar o retry.
     */
    Class<? extends Throwable>[] include() default {};

    /**
     * Define as exceções que não devem acionar o retry.
     * <p>
     * Caso uma exceção nessa lista seja lançada, o retry será interrompido.
     * </p>
     *
     * @return array de classes de exceções que devem ser excluídas do retry.
     */
    Class<? extends Throwable>[] exclude() default {};

    /**
     * Define o comportamento de backoff entre as tentativas de retry.
     * <p>
     * É possível configurar o tempo inicial de espera, o multiplicador
     * exponencial e o tempo máximo de espera entre as tentativas.
     * </p>
     *
     * @return configuração de backoff (padrão: {@link BackoffRetry} com valores padrão).
     */
    BackoffRetry backoff() default @BackoffRetry;

    /**
     * Define o nome do método de fallback a ser chamado caso todas as tentativas falhem.
     * <p>
     * O método de fallback deve estar na mesma classe que o método anotado e
     * possuir a mesma assinatura de parâmetros, além de um último parâmetro adicional
     * do tipo {@link Throwable} para receber a exceção original.
     * </p>
     *
     * @return nome do método de fallback (padrão: vazio, ou seja, sem fallback configurado).
     */
    String fallbackMethod() default "";
}
