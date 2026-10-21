package com.futurotech.legal.infrastructure.adapter.out.ai;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Genera embeddings vía Ollama POST /api/embeddings. Caché LRU en memoria para
 * evitar recomputar el vector de textos repetidos (consultas frecuentes).
 */
@Component
public class OllamaEmbeddingClient {

    private static final int CACHE_MAX = 256;

    private final WebClient webClient;
    private final String model;
    private final int timeoutSeconds;

    private final Map<String, List<Float>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Float>> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    public OllamaEmbeddingClient(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.embedding-model:nomic-embed-text}") String model,
            @Value("${ollama.timeout-seconds:120}") int timeoutSeconds) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<Float> embed(String text) {
        List<Float> cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> body = Map.of("model", model, "prompt", text);
        EmbeddingResponse resp = webClient.post()
                .uri("/api/embeddings")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .block(Duration.ofSeconds(timeoutSeconds));

        if (resp == null || resp.embedding() == null || resp.embedding().isEmpty()) {
            throw new DocumentGenerationException("Ollama devolvió un embedding vacío");
        }
        cache.put(text, resp.embedding());
        return resp.embedding();
    }

    record EmbeddingResponse(List<Float> embedding) {
    }
}
