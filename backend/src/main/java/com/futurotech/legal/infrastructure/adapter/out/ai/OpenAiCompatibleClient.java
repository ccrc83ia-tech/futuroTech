package com.futurotech.legal.infrastructure.adapter.out.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.LegalContext;
import com.futurotech.legal.domain.port.out.AIServicePort;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Adaptador de IA genérico sobre cualquier API compatible con OpenAI
 * (POST /chat/completions con {@code Authorization: Bearer}). Una sola clase
 * sirve a los tres roles del pipeline (generador, verificador de citas,
 * verificador de datos): se instancia tantas veces como roles en
 * {@link AiClientsConfig}, cada instancia con su propia URL, modelo, api-key y
 * su propio circuit breaker + retry (Resilience4j, instancia {@code name}).
 *
 * <p>Migrar de proveedor (Gemini, Groq, OpenRouter, OpenAI, DeepSeek…) solo
 * requiere cambiar URL + modelo + api-key por variables de entorno, sin tocar
 * código. Es el núcleo del diseño multi-proveedor costo-cero.
 */
public class OpenAiCompatibleClient implements AIServicePort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final String name;
    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final int timeoutSeconds;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public OpenAiCompatibleClient(String name, String baseUrl, String apiKey, String model,
                                  int maxTokens, int timeoutSeconds,
                                  CircuitBreakerRegistry cbRegistry, RetryRegistry retryRegistry) {
        this.name = name;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.circuitBreaker = cbRegistry.circuitBreaker(name);
        this.retry = retryRegistry.retry(name);
    }

    @Override
    public String generate(LegalContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new DocumentGenerationException(
                    "Proveedor de IA '" + name + "' no configurado: falta la API key.");
        }
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker,
                    Retry.decorateSupplier(retry, () -> callApi(context))).get();
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.error("[{}] circuit breaker ABIERTO: {}", name, e.getMessage());
            throw new DocumentGenerationException(
                    "Servicio de IA no disponible temporalmente. Intente en unos minutos.");
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("[{}] respondió {}: {}", name, e.getStatusCode(), e.getResponseBodyAsString());
            throw new DocumentGenerationException(mapHttpError(e));
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] error inesperado: {}", name, e.toString());
            throw new DocumentGenerationException(
                    "Error al generar el documento: " + e.getClass().getSimpleName());
        }
    }

    /** Traduce un error HTTP del proveedor de IA a un mensaje accionable para el usuario. */
    private static String mapHttpError(
            org.springframework.web.reactive.function.client.WebClientResponseException e) {
        int status = e.getStatusCode().value();
        return switch (status) {
            case 413 -> "El contenido enviado es demasiado grande. Reduzca el número o tamaño de "
                    + "los documentos adjuntos e intente de nuevo.";
            case 429 -> "Se alcanzó el límite de solicitudes del servicio de IA. "
                    + "Espere un momento e intente de nuevo.";
            case 401, 403 -> "Error de autenticación con el servicio de IA. "
                    + "Verifique la configuración (API key).";
            case 400 -> "El servicio de IA rechazó la solicitud. Puede que los documentos adjuntos "
                    + "excedan la capacidad del modelo; reduzca el contenido e intente de nuevo.";
            default -> "El servicio de IA devolvió un error (" + status + "). Intente de nuevo.";
        };
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
