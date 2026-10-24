package com.futurotech.legal.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.futurotech.legal.domain.port.out.AIServicePort;

/**
 * Health check de la IA: expone el estado del circuit breaker del generador
 * activo. Sin dependencias on-premise (el stack ya no usa Ollama; generación y
 * embeddings son APIs remotas). Se integra en /actuator/health como "ai".
 */
@Component("ai")
public class AiHealthIndicator implements HealthIndicator {

    private final AIServicePort generator;

    public AiHealthIndicator(@Qualifier("aiGenerator") AIServicePort generator) {
        this.generator = generator;
    }

    @Override
    public Health health() {
        String circuit = generator.circuitState();
        // OPEN indica fallos recientes del proveedor; CLOSED/HALF_OPEN se consideran operativos.
        if ("OPEN".equals(circuit)) {
            return Health.down().withDetail("circuit", circuit).build();
        }
        return Health.up().withDetail("circuit", circuit).build();
    }
}
