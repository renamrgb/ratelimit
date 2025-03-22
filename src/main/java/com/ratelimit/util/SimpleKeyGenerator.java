package com.ratelimit.util;

import com.ratelimit.spi.KeyGenerator;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementação simples de KeyGenerator que gera chaves baseadas no
 * identificador do recurso e objetos de contexto adicionais.
 */
public class SimpleKeyGenerator implements KeyGenerator {
    
    private static final String KEY_SEPARATOR = ":";
    
    @Override
    public String generateKey(String resourceId, Object... context) {
        Objects.requireNonNull(resourceId, "O identificador do recurso não pode ser nulo");
        
        if (context == null || context.length == 0) {
            return resourceId;
        }
        
        String contextString = Arrays.stream(context)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining(KEY_SEPARATOR));
        
        return contextString.isEmpty() ? resourceId : resourceId + KEY_SEPARATOR + contextString;
    }
} 