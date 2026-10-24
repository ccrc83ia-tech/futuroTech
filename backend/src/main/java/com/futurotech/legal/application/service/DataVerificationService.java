package com.futurotech.legal.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.futurotech.legal.domain.model.LegalContext;
import com.futurotech.legal.domain.model.VerificationResult;
import com.futurotech.legal.domain.port.out.AIServicePort;

/**
 * Verificación anti-alucinación de DATOS DUROS: comprueba la coherencia de los
 * cálculos, fechas, montos, porcentajes y plazos del borrador frente a los datos
 * del caso (caseFields + hechos aportados). Usa un tercer modelo de IA de FAMILIA
 * DISTINTA (rol "verificador de datos", por defecto DeepSeek vía OpenRouter),
 * fuerte en razonamiento matemático, para atrapar incoherencias numéricas que el
 * generador pudo introducir.
 *
 * <p>Tolerante a fallos: si no está configurado o falla, devuelve "omitido" sin
 * romper la generación. La revisión humana (DRAFT) es la salvaguarda final.
 */
@Service
public class DataVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DataVerificationService.class);
    private static final String ROLE = "data";

    private final AIServicePort verifier;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean dataEnabled;

    public DataVerificationService(
            @Qualifier("aiVerifierData") AIServicePort verifier,
            ObjectMapper objectMapper,
            @Value("${verification.enabled:true}") boolean enabled,
            @Value("${verification.data-enabled:true}") boolean dataEnabled) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.dataEnabled = dataEnabled;
    }

    /**
     * Verifica la coherencia de los datos duros del borrador.
     *
     * @param draft      texto generado.
     * @param caseData   datos del caso (campos limpios, sin claves técnicas "_").
     * @param factsBlock texto de los documentos aportados (hechos), puede ser vacío.
     */
    public VerificationResult verify(String draft, Map<String, String> caseData, String factsBlock) {
        if (!enabled || !dataEnabled) {
            return VerificationResult.skipped(ROLE, "deshabilitada por configuración");
        }
        if (draft == null || draft.isBlank()) {
            return VerificationResult.skipped(ROLE, "borrador vacío");
        }
        try {
            LegalContext ctx = LegalContext.of(
                    buildSystemPrompt(), buildUserPrompt(draft, caseData, factsBlock), null);
            String raw = verifier.generate(ctx);
            return parse(raw);
        } catch (Exception e) {
            log.warn("Verificación de datos no disponible: {}", e.getMessage());
            return VerificationResult.skipped(ROLE, "verificador no disponible");
        }
    }

    private static String buildSystemPrompt() {
        return "Eres un auditor de datos jurídicos, experto en cálculos y fechas. Tu única tarea es "
                + "detectar incoherencias NUMÉRICAS o de FECHAS entre el documento y los datos del caso: "
                + "montos mal sumados, porcentajes que no cuadran, fechas imposibles o contradictorias, "
                + "plazos mal calculados, cifras citadas que no aparecen en los datos del caso. "
                + "No redactas ni corriges: solo auditas. Responde EXCLUSIVAMENTE con un JSON válido: "
                + "{\"passed\": boolean, \"issues\": [\"incoherencia detectada\", ...], "
                + "\"summary\": \"resumen breve en español\"}. "
                + "Si un dato numérico del documento no tiene respaldo en los datos del caso, repórtalo. "
                + "Si todo es coherente, devuelve passed=true e issues vacío.";
    }

    private static String buildUserPrompt(String draft, Map<String, String> caseData, String factsBlock) {
        StringBuilder sb = new StringBuilder();
        sb.append("DATOS DEL CASO:\n");
        if (caseData != null && !caseData.isEmpty()) {
            caseData.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        } else {
            sb.append("(sin campos estructurados)\n");
        }
        if (factsBlock != null && !factsBlock.isBlank()) {
            sb.append("\nHECHOS APORTADOS:\n").append(factsBlock).append("\n");
        }
        sb.append("\nDOCUMENTO GENERADO:\n").append(draft)
                .append("\n\nDevuelve el JSON indicado. No agregues texto fuera del JSON.");
        return sb.toString();
    }

    private VerificationResult parse(String raw) {
        try {
            String json = CitationVerificationService.extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            boolean passed = node.path("passed").asBoolean(true);
            List<String> findings = new ArrayList<>();
            JsonNode issues = node.path("issues");
            if (issues.isArray()) {
                issues.forEach(n -> findings.add(n.asText()));
            }
            String summary = node.path("summary").asText("");
            boolean effectivePassed = passed && findings.isEmpty();
            return new VerificationResult(ROLE, effectivePassed, findings,
                    summary.isBlank()
                            ? (effectivePassed ? "Sin hallazgos." : "Incoherencias de datos detectadas.")
                            : summary);
        } catch (Exception e) {
            log.warn("No se pudo parsear la respuesta del verificador de datos: {}", e.getMessage());
            return VerificationResult.skipped(ROLE, "respuesta no parseable");
        }
    }
}
