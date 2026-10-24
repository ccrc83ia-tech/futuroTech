package com.futurotech.legal.domain.port.out;

import com.futurotech.legal.domain.model.LegalContext;

/**
 * Contrato agnóstico de IA: recibe un LegalContext y devuelve texto crudo.
 * Implementado por adaptadores sobre APIs compatibles con OpenAI (Gemini, Groq,
 * OpenRouter/DeepSeek…), uno por rol del pipeline (generador y verificadores).
 */
public interface AIServicePort {

    String generate(LegalContext context);

    /** Estado del circuit breaker, para el health check. */
    String circuitState();
}
