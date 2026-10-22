package com.futurotech.legal.infrastructure.adapter.out.ai;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.LegalContext;
import com.futurotech.legal.domain.port.out.AIServicePort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Adaptador de IA sobre Groq (API compatible con OpenAI: POST /chat/completions).
 * Proveedor de generación por defecto. Protegido por circuit breaker + retry
 * (Resilience4j, instancia "groq"). Intercambiable vía {@link AIServicePort}.
 *
 * <p>El {@code base-url} es configurable: migrar a otra API compatible con OpenAI
 * (p.ej. una API paga) solo requiere cambiar URL, modelo y api-key, sin tocar código.
 *
 * <p>Se activa salvo que se fije {@code ai.provider=ollama}.
 */
@Component
@Primary
@ConditionalOnProperty(name = "ai.provider", havingValue = "groq", matchIfMissing = true)
public class GroqClient implements AIServicePort {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final int timeoutSeconds;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public GroqClient(
            @Value("${ai.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${ai.groq.api-key:}") String apiKey,
            @Value("${ai.groq.model:openai/gpt-oss-120b}") String model,
            @Value("${ai.groq.max-tokens:8000}") int maxTokens,
            @Value("${ai.groq.timeout-seconds:120}") int timeoutSeconds,
            CircuitBreakerRegistry cbRegistry,
            RetryRegistry retryRegistry) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.circuitBreaker = cbRegistry.circuitBreaker("groq");
        this.retry = retryRegistry.retry("groq");
    }

    @Override
    public String generate(LegalContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new DocumentGenerationException(
                    "Proveedor de IA no configurado: falta GROQ_API_KEY.");
        }
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker,
                    Retry.decorateSupplier(retry, () -> callApi(context))).get();
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.error("Groq circuit breaker ABIERTO: {}", e.getMessage());
            throw new DocumentGenerationException(
                    "Servicio de IA no disponible temporalmente. Intente en unos minutos.");
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado llamando a Groq: {}", e.toString());
            throw new DocumentGenerationException(
                    "Error al generar el documento: " + e.getClass().getSimpleName());
        }
    }

    private String callApi(LegalContext context) {
        double temperature = Math.max(0.0, Math.min(context.temperature(), 1.0));

        List<Map<String, Object>> messages = new ArrayList<>();
        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", context.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", context.userPrompt()));

        int effectiveMaxTokens = context.maxOutputTokens() != null && context.maxOutputTokens() > 0
                ? context.maxOutputTokens()
                : maxTokens;

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", effectiveMaxTokens,
                "temperature", temperature,
                "messages", messages);

        ChatCompletionResponse resp = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .block(Duration.ofSeconds(timeoutSeconds));

        if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
            throw new DocumentGenerationException("Respuesta vacía del proveedor de IA");
        }
        String content = resp.choices().get(0).message().content();
        if (content == null || content.isBlank()) {
            throw new DocumentGenerationException("Respuesta vacía del proveedor de IA");
        }
        return content;
    }

    @Override
    public String circuitState() {
        return circuitBreaker.getState().name();
    }

    /** Subconjunto de la respuesta OpenAI-compatible de /chat/completions. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletionResponse(List<Choice> choices) {

        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        record Choice(Message message) {
        }

        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        record Message(String role, String content) {
        }
    }
}
