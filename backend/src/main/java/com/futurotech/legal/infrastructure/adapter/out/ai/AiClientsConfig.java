package com.futurotech.legal.infrastructure.adapter.out.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.futurotech.legal.domain.port.out.AIServicePort;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Cablea los tres roles de IA del pipeline anti-alucinación, cada uno como un
 * {@link OpenAiCompatibleClient} independiente con su propia URL, modelo, api-key
 * y circuit breaker. Todos configurables por variables de entorno (costo cero por
 * defecto: Gemini Flash + Groq/Llama + DeepSeek vía OpenRouter).
 *
 * <ul>
 *   <li><b>aiGenerator</b> (@Primary): redacta el documento. Default Gemini Flash.</li>
 *   <li><b>aiVerifierCitations</b>: verifica que las normas citadas estén en los
 *       fragmentos RAG. Default Groq + Llama.</li>
 *   <li><b>aiVerifierData</b>: verifica coherencia de datos duros (cálculos, fechas,
 *       montos, plazos). Default DeepSeek vía OpenRouter.</li>
 * </ul>
 */
@Configuration
public class AiClientsConfig {

    private final CircuitBreakerRegistry cbRegistry;
    private final RetryRegistry retryRegistry;

    public AiClientsConfig(CircuitBreakerRegistry cbRegistry, RetryRegistry retryRegistry) {
        this.cbRegistry = cbRegistry;
        this.retryRegistry = retryRegistry;
    }

    @Bean
    @Primary
    AIServicePort aiGenerator(
            @Value("${ai.generator.base-url:https://generativelanguage.googleapis.com/v1beta/openai}") String baseUrl,
            @Value("${ai.generator.api-key:}") String apiKey,
            @Value("${ai.generator.model:gemini-2.5-flash}") String model,
            @Value("${ai.generator.max-tokens:8000}") int maxTokens,
            @Value("${ai.generator.timeout-seconds:120}") int timeoutSeconds) {
        return new OpenAiCompatibleClient("ai-generator", baseUrl, apiKey, model,
                maxTokens, timeoutSeconds, cbRegistry, retryRegistry);
    }

    @Bean
    AIServicePort aiVerifierCitations(
            @Value("${ai.verifier-citations.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${ai.verifier-citations.api-key:}") String apiKey,
            @Value("${ai.verifier-citations.model:llama-3.3-70b-versatile}") String model,
            @Value("${ai.verifier-citations.max-tokens:4000}") int maxTokens,
            @Value("${ai.verifier-citations.timeout-seconds:90}") int timeoutSeconds) {
        return new OpenAiCompatibleClient("ai-verifier-citations", baseUrl, apiKey, model,
                maxTokens, timeoutSeconds, cbRegistry, retryRegistry);
    }

    @Bean
    AIServicePort aiVerifierData(
            @Value("${ai.verifier-data.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${ai.verifier-data.api-key:}") String apiKey,
            @Value("${ai.verifier-data.model:deepseek/deepseek-chat-v3:free}") String model,
            @Value("${ai.verifier-data.max-tokens:4000}") int maxTokens,
            @Value("${ai.verifier-data.timeout-seconds:90}") int timeoutSeconds) {
        return new OpenAiCompatibleClient("ai-verifier-data", baseUrl, apiKey, model,
                maxTokens, timeoutSeconds, cbRegistry, retryRegistry);
    }
}
