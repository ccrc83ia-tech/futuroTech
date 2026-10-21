package com.futurotech.legal.infrastructure.adapter.out.ai;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.LegalContext;
import com.futurotech.legal.domain.port.out.AIServicePort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Adaptador de IA sobre Ollama (local). Llama a POST /api/generate con stream=false.
 * Protegido por circuit breaker + retry (Resilience4j). Intercambiable vía AIServicePort.
 */
@Component
public class OllamaClient implements AIServicePort {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final WebClient webClient;
    private final String model;
    private final int timeoutSeconds;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public OllamaClient(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.generation-model:qwen2.5:7b}") String model,
            @Value("${ollama.timeout-seconds:120}") int timeoutSeconds,
            CircuitBreakerRegistry cbRegistry,
            RetryRegistry retryRegistry) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.circuitBreaker = cbRegistry.circuitBreaker("ollama");
        this.retry = retryRegistry.retry("ollama");
    }

    @Override
    public String generate(LegalContext context) {
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker,
                    Retry.decorateSupplier(retry, () -> callApi(context))).get();
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.error("Ollama circuit breaker ABIERTO: {}", e.getMessage());
            throw new DocumentGenerationException(
                    "Servicio de IA no disponible temporalmente. Intente en unos minutos.");
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado llamando a Ollama: {}", e.toString());
            throw new DocumentGenerationException(
                    "Error al generar el documento: " + e.getClass().getSimpleName());
        }
    }

    private String callApi(LegalContext context) {
        double temperature = Math.max(0.0, Math.min(context.temperature(), 1.0));
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", context.userPrompt(),
                "system", context.systemPrompt(),
                "stream", false,
                "options", Map.of("temperature", temperature));

        OllamaGenerateResponse resp = webClient.post()
                .uri("/api/generate")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OllamaGenerateResponse.class)
                .block(Duration.ofSeconds(timeoutSeconds));

        if (resp == null || resp.response() == null || resp.response().isBlank()) {
            throw new DocumentGenerationException("Respuesta vacía del proveedor de IA");
        }
        return resp.response();
    }

    @Override
    public String circuitState() {
        return circuitBreaker.getState().name();
    }

    /** Subconjunto de la respuesta de Ollama /api/generate. */
    record OllamaGenerateResponse(String response, boolean done) {
    }
}
