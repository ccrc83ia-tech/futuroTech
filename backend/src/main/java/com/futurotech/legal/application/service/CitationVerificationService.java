package com.futurotech.legal.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Verificación anti-alucinación de CITAS: comprueba que cada norma citada en el
 * borrador (leyes, decretos, artículos, sentencias) esté respaldada por los
 * fragmentos RAG que se usaron para generarlo. Usa un modelo de IA DISTINTO al
 * generador (rol "verificador de citas") para no heredar sus sesgos.
 *
 * <p>Es tolerante a fallos: si el verificador no está configurado o falla, NO
 * rompe la generación — devuelve un resultado "omitido" y el documento sigue su
 * curso normal a DRAFT (la revisión humana es la red de seguridad final).
 */
@Service
public class CitationVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CitationVerificationService.class);
    private static final String ROLE = "citations";

    /** Detecta menciones normativas típicas en español jurídico colombiano. */
    private static final Pattern NORM_PATTERN = Pattern.compile(
            "(?i)\\b(?:ley|decreto|art[íi]culo|art\\.|sentencia|resoluci[óo]n|c[óo]digo)\\s+[\\wº°.\\-/]+"
    );

    private final AIServicePort verifier;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean citationsEnabled;

    public CitationVerificationService(
            @Qualifier("aiVerifierCitations") AIServicePort verifier,
            ObjectMapper objectMapper,
            @Value("${verification.enabled:true}") boolean enabled,
            @Value("${verification.citations-enabled:true}") boolean citationsEnabled) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.citationsEnabled = citationsEnabled;
    }

    /**
     * Verifica el borrador contra los fragmentos autorizados.
     *
     * @param draft  texto generado por el modelo.
     * @param chunks fragmentos RAG que se inyectaron como fuentes autorizadas.
     * @return resultado con las citas sin respaldo detectadas.
     */
    public VerificationResult verify(String draft, List<String> chunks) {
        if (!enabled || !citationsEnabled) {
            return VerificationResult.skipped(ROLE, "deshabilitada por configuración");
        }
        if (draft == null || draft.isBlank()) {
            return VerificationResult.skipped(ROLE, "borrador vacío");
        }
        // Sin fuentes no hay nada contra qué verificar citas textuales.
        if (chunks == null || chunks.isEmpty()) {
            return VerificationResult.skipped(ROLE, "sin fragmentos RAG");
        }

        List<String> citedNorms = extractCitedNorms(draft);
        if (citedNorms.isEmpty()) {
            return VerificationResult.ok(ROLE);
        }

        try {
            String sources = String.join("\n\n---\n\n", chunks);
            LegalContext ctx = LegalContext.of(buildSystemPrompt(), buildUserPrompt(citedNorms, sources), null);
            String raw = verifier.generate(ctx);
            return parse(raw);
        } catch (Exception e) {
            // No romper la generación por un fallo del verificador.
            log.warn("Verificación de citas no disponible: {}", e.getMessage());
            return VerificationResult.skipped(ROLE, "verificador no disponible");
        }
    }

    /** Extrae las menciones normativas del borrador (deduplicadas, en orden). */
    static List<String> extractCitedNorms(String draft) {
        List<String> norms = new ArrayList<>();
        Matcher m = NORM_PATTERN.matcher(draft);
        while (m.find()) {
            String norm = m.group().strip();
            if (!norms.contains(norm)) {
                norms.add(norm);
            }
        }
        return norms;
    }

    private static String buildSystemPrompt() {
        return "Eres un auditor jurídico estricto. Tu única tarea es verificar si cada norma citada "
                + "en un documento está respaldada TEXTUALMENTE por las fuentes autorizadas provistas. "
                + "No redactas ni corriges: solo auditas. Responde EXCLUSIVAMENTE con un JSON válido "
                + "con esta forma exacta: "
                + "{\"passed\": boolean, \"unsupported\": [\"norma citada sin respaldo\", ...], "
                + "\"summary\": \"resumen breve en español\"}. "
                + "Una norma está respaldada solo si aparece de forma equivalente en las fuentes. "
                + "Si no estás seguro, considérala NO respaldada.";
    }

    private static String buildUserPrompt(List<String> citedNorms, String sources) {
        return "NORMAS CITADAS EN EL DOCUMENTO:\n- " + String.join("\n- ", citedNorms)
                + "\n\nFUENTES AUTORIZADAS:\n" + sources
                + "\n\nDevuelve el JSON indicado. No agregues texto fuera del JSON.";
    }

    private VerificationResult parse(String raw) {
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            boolean passed = node.path("passed").asBoolean(true);
            List<String> findings = new ArrayList<>();
            JsonNode unsupported = node.path("unsupported");
            if (unsupported.isArray()) {
                unsupported.forEach(n -> findings.add(n.asText()));
            }
            String summary = node.path("summary").asText("");
            // Si hay hallazgos, no pasó, aunque el modelo diga lo contrario.
            boolean effectivePassed = passed && findings.isEmpty();
            return new VerificationResult(ROLE, effectivePassed, findings,
                    summary.isBlank() ? (effectivePassed ? "Sin hallazgos." : "Citas sin respaldo detectadas.") : summary);
        } catch (Exception e) {
            log.warn("No se pudo parsear la respuesta del verificador de citas: {}", e.getMessage());
            return VerificationResult.skipped(ROLE, "respuesta no parseable");
        }
    }

    /** Extrae el primer objeto JSON del texto (los modelos a veces lo envuelven en prosa). */
    static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}
