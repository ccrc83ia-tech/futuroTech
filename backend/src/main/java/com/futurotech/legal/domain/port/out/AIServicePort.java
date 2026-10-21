package com.futurotech.legal.domain.port.out;

import com.futurotech.legal.domain.model.LegalContext;

/**
 * Contrato agnóstico de IA: recibe un LegalContext y devuelve texto crudo.
 * Implementado por el adaptador de Ollama (o cualquier otro proveedor).
 */
public interface AIServicePort {

    String generate(LegalContext context);

    /** Estado del circuit breaker, para el health check. */
    String circuitState();
}
