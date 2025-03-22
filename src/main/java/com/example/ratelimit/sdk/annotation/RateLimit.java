package com.example.ratelimit.sdk.annotation;

import java.lang.annotation.*;

/**
 * Anotação para aplicar controle de rate limit em métodos.
 * <p>
 * Essa anotação permite limitar o número de chamadas permitidas em um determinado
 * intervalo de tempo, com suporte para regras configuráveis de retry, caso o método
 * falhe e esteja habilitado.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface RateLimit {

    /**
     * Define a chave única associada ao controle de rate limit.
     * <p>
     * Essa chave é usada para identificar o bucket de tokens associado ao método.
     * Geralmente, pode ser o nome do método ou uma chave personalizada.
     * </p>
     *
     * @return chave única do bucket de rate limit.
     */
    String key();

    /**
     * Define o limite máximo de requisições permitidas no intervalo de tempo definido.
     *
     * @return número máximo de requisições permitidas (padrão: 10).
     */
    int limit() default 10;

    /**
     * Define a unidade de tempo associada ao controle de rate limit.
     * <p>
     * Essa unidade determina o intervalo em que o limite de requisições será aplicado.
     * Por exemplo, 10 requisições por segundo ou 100 requisições por hora.
     * </p>
     *
     * @return unidade de tempo associada ao rate limit (padrão: {@link TimeUnit#SECONDS}).
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
    
    /**
     * Configura um limite adicional (overdraft) que permite ultrapassar temporariamente
     * o limite definido. Isso é útil para lidar com picos temporários de tráfego.
     * <p>
     * Um valor de 0 significa que não há overdraft permitido.
     * </p>
     * 
     * @return número de requisições adicionais permitidas temporariamente (padrão: 0).
     */
    int overdraft() default 0;
    
    /**
     * Define se o bucket deve usar refill greedy (recarrega todos os tokens de uma vez)
     * ou interval (adiciona tokens incrementalmente). O modo greedy é mais adequado
     * para APIs que precisam lidar com picos de tráfego, enquanto o interval distribui
     * melhor o tráfego ao longo do tempo.
     * 
     * @return true para usar refill greedy, false para usar interval (padrão: true).
     */
    boolean greedyRefill() default true;

    /**
     * Define a configuração de retry associada ao método anotado.
     * <p>
     * Essa configuração determina se tentativas adicionais devem ser realizadas em
     * caso de falha, e como o comportamento de retry deve ser gerenciado.
     * Por padrão, o retry está desativado com {@code maxAttempts = 0}.
     * </p>
     *
     * @return configuração de retry (padrão: retry desativado).
     */
    Retry retry() default @Retry(maxAttempts = 0);

    /**
     * Enum para definir as unidades de tempo utilizadas no rate limit.
     */
    enum TimeUnit {
        SECONDS(1_000_000_000),
        MINUTES(60L * 1_000_000_000),
        HOURS(3600L * 1_000_000_000);

        private final long timeInNanos;

        /**
         * Construtor do enum, associa o valor em nanossegundos à unidade de tempo.
         *
         * @param timeInNanos valor em nanossegundos para a unidade de tempo.
         */
        TimeUnit(long timeInNanos) {
            this.timeInNanos = timeInNanos;
        }

        /**
         * Retorna o valor da unidade de tempo em nanossegundos.
         *
         * @return valor da unidade de tempo em nanossegundos.
         */
        public long getTimeInNanos() {
            return timeInNanos;
        }
    }
}
