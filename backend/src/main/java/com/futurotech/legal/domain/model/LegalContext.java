package com.futurotech.legal.domain.model;

import java.util.Map;

/**
 * Contexto agnóstico ensamblado por el caso de uso y consumido por cualquier
 * proveedor de IA (Ollama, Gemini, etc.). No depende de ningún SDK concreto.
 */
public record LegalContext(
        String systemPrompt,
        String userPrompt,
        Map<String, String> metadata,
        double temperature,
        Integer maxOutputTokens
) {
    public LegalContext {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static LegalContext of(String systemPrompt, String userPrompt, Map<String, String> metadata) {
        return new LegalContext(systemPrompt, userPrompt, metadata, 0.3, null);
    }
}
