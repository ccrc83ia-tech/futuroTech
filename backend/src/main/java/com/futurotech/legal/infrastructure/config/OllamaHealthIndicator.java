package com.futurotech.legal.infrastructure.config;

import com.futurotech.legal.domain.port.out.AIServicePort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Health check profundo de la IA: verifica conectividad con Ollama y expone el
 * estado del circuit breaker. Se integra en /actuator/health.
 */
@Component("ollama")
public class OllamaHealthIndicator implements HealthIndicator {

    private final WebClient webClient;
    private final AIServicePort ai;

    public OllamaHealthIndicator(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            AIServicePort ai) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.ai = ai;
    }

    @Override
    public Health health() {
        String circuit = ai.circuitState();
        try {
            webClient.get().uri("/api/tags").retrieve().toBodilessEntity().block(Duration.ofSeconds(3));
            return Health.up().withDetail("circuit", circuit).build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("circuit", circuit)
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
