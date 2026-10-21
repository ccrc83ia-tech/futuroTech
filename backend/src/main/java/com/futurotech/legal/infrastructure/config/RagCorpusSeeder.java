package com.futurotech.legal.infrastructure.config;

import com.futurotech.legal.domain.port.out.KnowledgeBasePort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Siembra el corpus legal base del RAG en el primer arranque. Para cada
 * plantilla, si aún no tiene documentos indexados, ingesta los textos legales
 * asociados (que se fragmentan y embeben vía Ollama/pgvector por el puerto).
 *
 * Idempotente: usa hasDocuments() para no reingresar. Tolerante a fallos: si
 * Ollama no está disponible, registra una advertencia y no interrumpe el
 * arranque (el modo estricto operará sin fuentes hasta que se reintente).
 */
@Component
public class RagCorpusSeeder {

    private static final Logger log = LoggerFactory.getLogger(RagCorpusSeeder.class);

    private static final String CORPUS_PATH = "rag-corpus/";

    /** Plantilla -> documentos legales base (nombre lógico -> archivo en resources). */
    private static final Map<String, Map<String, String>> CORPUS_BY_TEMPLATE = Map.of(
            "PETICION-GENERAL-V1", Map.of(
                    "Constitución Política — Art. 23, 29", "constitucion-art23.txt",
                    "Ley 1755 de 2015", "ley-1755-2015.txt"),
            "PETICION-PENSIONAL-V1", Map.of(
                    "Constitución Política — Art. 48", "constitucion-art23.txt",
                    "Ley 1755 de 2015", "ley-1755-2015.txt"),
            "ACCION-TUTELA-V1", Map.of(
                    "Constitución Política — Art. 86", "constitucion-art23.txt",
                    "Decreto 2591 de 1991", "decreto-2591-1991.txt"));

    private final KnowledgeBasePort knowledgeBase;

    public RagCorpusSeeder(KnowledgeBasePort knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public void seed() {
        for (var entry : CORPUS_BY_TEMPLATE.entrySet()) {
            String templateId = entry.getKey();
            try {
                if (knowledgeBase.hasDocuments(templateId)) {
                    continue; // ya sembrado
                }
                for (var doc : entry.getValue().entrySet()) {
                    String text = readResource(CORPUS_PATH + doc.getValue());
                    int chunks = knowledgeBase.ingest(templateId, doc.getKey(), text);
                    log.info("Corpus RAG: '{}' -> {} ({} chunks)", templateId, doc.getKey(), chunks);
                }
            } catch (Exception e) {
                // No romper el arranque si Ollama/pgvector aún no responden.
                log.warn("No se pudo sembrar el corpus RAG de '{}': {}. "
                        + "Se reintentará en el próximo arranque.", templateId, e.getMessage());
                return;
            }
        }
    }

    private static String readResource(String path) throws IOException {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    // Visible para pruebas.
    static List<String> templateIds() {
        return List.copyOf(CORPUS_BY_TEMPLATE.keySet());
    }
}
