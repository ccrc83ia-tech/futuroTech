package com.futurotech.legal.application.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.exception.TenantNotFoundException;
import com.futurotech.legal.domain.model.CaseAttachment;
import com.futurotech.legal.domain.model.DocumentStatus;
import com.futurotech.legal.domain.model.GeneratedDocument;
import com.futurotech.legal.domain.model.LegalContext;
import com.futurotech.legal.domain.model.Template;
import com.futurotech.legal.domain.model.TenantConfig;
import com.futurotech.legal.domain.port.in.GenerateDocumentUseCase;
import com.futurotech.legal.domain.port.out.AIServicePort;
import com.futurotech.legal.domain.port.out.FileExporterPort;
import com.futurotech.legal.domain.port.out.GeneratedDocumentPort;
import com.futurotech.legal.domain.port.out.KnowledgeBasePort;
import com.futurotech.legal.domain.port.out.TemplateRepositoryPort;
import com.futurotech.legal.domain.port.out.TenantRepositoryPort;

/**
 * Orquestador de generación: resuelve firma y plantilla, compone el prompt con
 * modo RAG estricto (anti-alucinación), llama a la IA, exporta el documento con
 * el formato de la firma y registra la auditoría (estado inicial DRAFT).
 */
@Service
public class GenerateDocumentService implements GenerateDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerateDocumentService.class);
    private static final int RAG_RESULTS = 10;
    private static final int MAX_STRUCTURE_LINES = 80;

    private final TenantRepositoryPort tenantRepo;
    private final TemplateRepositoryPort templateRepo;
    private final AIServicePort ai;
    private final FileExporterPort fileExporter;
    private final KnowledgeBasePort knowledgeBase;
    private final GeneratedDocumentPort auditTrail;

    public GenerateDocumentService(TenantRepositoryPort tenantRepo, TemplateRepositoryPort templateRepo,
                                   AIServicePort ai, FileExporterPort fileExporter,
                                   KnowledgeBasePort knowledgeBase, GeneratedDocumentPort auditTrail) {
        this.tenantRepo = tenantRepo;
        this.templateRepo = templateRepo;
        this.ai = ai;
        this.fileExporter = fileExporter;
        this.knowledgeBase = knowledgeBase;
        this.auditTrail = auditTrail;
    }

    @Override
    public GeneratedDocument generate(GenerateCommand cmd) {
        UUID transactionId = UUID.randomUUID();
        log.info("[{}] Inicio generación — tenant={} template={}", transactionId, cmd.tenantId(), cmd.templateId());

        TenantConfig tenant = tenantRepo.findById(cmd.tenantId())
                .orElseThrow(() -> new TenantNotFoundException(cmd.tenantId()));
        Template template = templateRepo.findById(cmd.templateId())
                .orElseThrow(() -> new DocumentGenerationException("Plantilla no encontrada: " + cmd.templateId()));

        String formatStructure = template.hasFormatDoc()
                ? extractFormatStructure(template.formatDocBytes()) : null;

        LegalContext context = composePrompt(tenant, template, cmd, formatStructure);

        String rawText;
        try {
            rawText = ai.generate(context);
            log.info("[{}] IA completó — {} chars", transactionId, rawText.length());
        } catch (DocumentGenerationException e) {
            log.error("[{}] Falla de IA: {}", transactionId, e.getMessage());
            throw e;
        }

        String fileName = transactionId + "_" + cmd.templateId() + ".docx";
        String saved = fileExporter.export(rawText, tenant.branding(), fileName, template.formatDocBytes());

        GeneratedDocument doc = new GeneratedDocument(
                transactionId, cmd.templateId(), null, cmd.authorUserId(), cmd.entityId(),
                cmd.caseFields(), DocumentStatus.DRAFT, null, saved, sha256(rawText),
                Instant.now(), null, null);
        auditTrail.save(doc);
        log.info("[{}] Documento registrado (DRAFT): {}", transactionId, saved);
        return doc;
    }

    // --- Composición de prompt + RAG estricto (porta la lógica del sistema Python) ---

    private LegalContext composePrompt(TenantConfig tenant, Template template,
                                       GenerateCommand cmd, String formatStructure) {
        List<String> rules = (cmd.selectedRules() != null && !cmd.selectedRules().isEmpty())
                ? cmd.selectedRules()
                : (!template.legalRules().isEmpty() ? template.legalRules() : tenant.legalRules());

        String rulesBlock = rules.isEmpty() ? ""
                : rules.stream().map(r -> "- " + r).reduce((a, b) -> a + "\n" + b).orElse("");

        String basePrompt = firstNonBlank(template.systemPrompt(), tenant.systemPrompt(),
                "Eres un abogado experto. Genera documentos jurídicos formales con lenguaje preciso y respetuoso.");

        StringBuilder systemPrompt = new StringBuilder(basePrompt);
        if (!rulesBlock.isEmpty()) {
            systemPrompt.append("\n\nReglas jurídicas aplicables:\n").append(rulesBlock);
        }

        if (formatStructure != null && !formatStructure.isBlank()) {
            systemPrompt.append("\n\n=== ESTRUCTURA OBLIGATORIA DEL DOCUMENTO ===\n")
                    .append("INSTRUCCIÓN ABSOLUTA: El documento generado DEBE seguir EXACTAMENTE la siguiente ")
                    .append("estructura de secciones y orden. No agregues, elimines ni reordenes secciones. ")
                    .append("Respeta los títulos, subtítulos y el orden tal como aparecen:\n\n")
                    .append(formatStructure).append("\n=== FIN DE ESTRUCTURA OBLIGATORIA ===");
        }

        // RAG
        boolean ragStrict = cmd.ragStrict();
        boolean hasIndexedDocs = knowledgeBase.hasDocuments(template.templateId());
        String ragContext = "";
        if (hasIndexedDocs) {
            List<String> queryParts = new ArrayList<>();
            queryParts.add(template.templateId());
            queryParts.addAll(rules);
            cleanMetadata(cmd.caseFields()).values().forEach(v -> {
                if (v != null && v.length() > 20) {
                    queryParts.add(v);
                }
            });
            List<String> chunks = knowledgeBase.search(
                    template.templateId(), String.join(" ", queryParts), RAG_RESULTS);
            if (!chunks.isEmpty()) {
                ragContext = String.join("\n\n---\n\n", chunks);
                log.info("RAG: inyectados {} fragmentos ({} chars)", chunks.size(), ragContext.length());
            }
        }

        if (ragStrict) {
            if (!ragContext.isEmpty()) {
                systemPrompt.append("\n\n=== FUENTES NORMATIVAS AUTORIZADAS ===\n")
                        .append("INSTRUCCIÓN ABSOLUTA: El documento SOLO puede contener citas, artículos, ")
                        .append("decretos, leyes y jurisprudencia que aparezcan TEXTUALMENTE en los fragmentos ")
                        .append("de abajo. Queda PROHIBIDO usar conocimiento propio, inventar contenido normativo ")
                        .append("o citar cualquier norma que no esté en estas fuentes. Si un argumento no tiene ")
                        .append("respaldo en los fragmentos, NO lo incluyas.\n\n")
                        .append(ragContext).append("\n=== FIN DE FUENTES AUTORIZADAS ===");
            } else {
                systemPrompt.append("\n\nINSTRUCCIÓN ABSOLUTA — MODO ESTRICTO: ")
                        .append(hasIndexedDocs
                                ? "Los documentos de referencia no contienen fragmentos relevantes para este caso. "
                                : "No hay documentos de referencia cargados para esta plantilla. ")
                        .append("Queda PROHIBIDO citar, transcribir o mencionar el contenido de cualquier ")
                        .append("artículo, ley, decreto o sentencia. Puedes mencionar los nombres de las normas ")
                        .append("listadas en las reglas jurídicas, pero NUNCA su texto. Donde iría una cita ")
                        .append("textual escribe exactamente: [texto no disponible en fuentes autorizadas].");
            }
        } else if (!ragContext.isEmpty()) {
            systemPrompt.append("\n\nDocumentos de referencia (prioriza estas fuentes, puedes complementar ")
                    .append("con tu conocimiento jurídico):\n\n").append(ragContext);
        }

        // Documentos aportados del caso (hechos). Son del caso concreto, no fuentes
        // normativas, por lo que se inyectan SIEMPRE, al margen del modo estricto.
        String attachmentsBlock = buildAttachmentsBlock(cmd.caseAttachments());
        if (!attachmentsBlock.isEmpty()) {
            systemPrompt.append("\n\n=== DOCUMENTOS APORTADOS DEL CASO (HECHOS) ===\n")
                    .append("El usuario adjuntó los siguientes documentos del caso concreto (p. ej. el ")
                    .append("derecho de petición negado que motiva una tutela). Úsalos como HECHOS para dar ")
                    .append("mayor solidez al documento: extrae fechas, entidades, pretensiones y respuestas, ")
                    .append("y referéncialos con precisión. Son hechos aportados por la parte, NO fuentes ")
                    .append("normativas: no los uses para citar leyes, decretos ni jurisprudencia.\n\n")
                    .append(attachmentsBlock)
                    .append("\n=== FIN DE DOCUMENTOS APORTADOS ===");
            log.info("Adjuntos del caso: {} documento(s) inyectado(s) en el prompt",
                    cmd.caseAttachments().size());
        }

        Map<String, String> clean = cleanMetadata(cmd.caseFields());
        String userPrompt = "Genera el documento con plantilla '" + template.templateId()
                + "' usando estos datos del caso:\n" + clean;

        return LegalContext.of(systemPrompt.toString(), userPrompt, clean);
    }

    /** Concatena el texto de los adjuntos del caso, cada uno con su nombre como encabezado. */
    private static String buildAttachmentsBlock(List<CaseAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CaseAttachment att : attachments) {
            if (att != null && att.hasText()) {
                if (sb.length() > 0) {
                    sb.append("\n\n---\n\n");
                }
                sb.append("[DOCUMENTO: ").append(att.fileName()).append("]\n")
                        .append(att.extractedText());
            }
        }
        return sb.toString();
    }

    /** Extrae títulos de sección y estructura del .docx modelo para guiar a la IA. */
    static String extractFormatStructure(byte[] formatDocBytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(formatDocBytes))) {
            List<String> lines = new ArrayList<>();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText() == null ? "" : para.getText().strip();
                if (text.isEmpty()) {
                    continue;
                }
                String style = para.getStyle() == null ? "" : para.getStyle();
                boolean isHeading = style.toLowerCase().contains("heading");
                boolean isBold = para.getRuns().stream()
                        .anyMatch(r -> Boolean.TRUE.equals(r.isBold()) && r.text() != null && !r.text().isBlank());
                if (isHeading || isBold || text.equals(text.toUpperCase())) {
                    lines.add("[SECCIÓN] " + text);
                } else {
                    lines.add(text);
                }
                if (lines.size() >= MAX_STRUCTURE_LINES) {
                    break;
                }
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            log.warn("No se pudo extraer la estructura del documento modelo: {}", e.getMessage());
            return null;
        }
    }

    private static Map<String, String> cleanMetadata(Map<String, String> metadata) {
        Map<String, String> clean = new LinkedHashMap<>();
        metadata.forEach((k, v) -> {
            if (!k.startsWith("_")) {
                clean.put(k, v);
            }
        });
        return clean;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
