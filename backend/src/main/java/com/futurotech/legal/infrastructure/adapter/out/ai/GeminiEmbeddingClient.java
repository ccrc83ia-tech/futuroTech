package com.futurotech.legal.infrastructure.adapter.out.ai;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.port.out.EmbeddingPort;

/**
 * Genera embeddings vía la API de Gemini (POST {model}:embedContent). Usa
 * {@code outputDimensionality} para fijar la dimensión en 768, de modo que
 * encaja en el esquema existente {@code vector(768)} sin migración. Caché LRU en
 * memoria para no recomputar vectores de textos repetidos (consultas frecuentes).
 *
 * <p>Reemplaza al antiguo cliente de embeddings on-premise (Ollama): elimina la
 * dependencia de un servicio local y permite correr en una EC2 pequeña.
 */
@Component
public class GeminiEmbeddingClient implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);
    private static final int CACHE_MAX = 256;

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final int timeoutSeconds;

    private final Map<String, List<Float>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Float>> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    public GeminiEmbeddingClient(
            @Value("${embeddings.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${embeddings.api-key:}") String apiKey,
            @Value("${embeddings.model:gemini-embedding-001}") String model,
            @Value("${embeddings.dimensions:768}") int dimensions,
            @Value("${embeddings.timeout-seconds:60}") int timeoutSeconds) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public List<Float> embed(String text) {
        List<Float> cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new DocumentGenerationException(
                    "Embeddings no configurados: falta GEMINI_API_KEY.");
        }

        Map<String, Object> body = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "outputDimensionality", dimensions);

        EmbedContentResponse resp;
        try {
            resp = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{model}:embedContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(EmbedContentResponse.class)
                    .block(Duration.ofSeconds(timeoutSeconds));
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("Gemini embeddings respondió {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new DocumentGenerationException(
                    "Error al generar embeddings (" + e.getStatusCode().value() + ").");
        } catch (Exception e) {
            log.error("Error inesperado generando embeddings: {}", e.toString());
            throw new DocumentGenerationException(
                    "Error al generar embeddings: " + e.getClass().getSimpleName());
        }

        if (resp == null || resp.embedding() == null || resp.embedding().values() == null
                || resp.embedding().values().isEmpty()) {
            throw new DocumentGenerationException("Gemini devolvió un embedding vacío");
        }
        List<Float> values = resp.embedding().values();
        if (values.size() != dimensions) {
            log.warn("Embedding de dimensión {} (esperado {}). Verifique embeddings.dimensions.",
                    values.size(), dimensions);
        }
        cache.put(text, values);
        return values;
    }

    /** Subconjunto de la respuesta de Gemini :embedContent. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record EmbedContentResponse(Embedding embedding) {

        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        record Embedding(List<Float> values) {
        }
    }
}
