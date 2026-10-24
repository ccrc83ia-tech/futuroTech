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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.exception.ResourceNotFoundException;
import com.futurotech.legal.domain.exception.TenantNotFoundException;
import com.futurotech.legal.domain.model.CaseAttachment;
import com.futurotech.legal.domain.model.DocumentStatus;
import com.futurotech.legal.domain.model.GeneratedDocument;
import com.futurotech.legal.domain.model.LegalContext;
import com.futurotech.legal.domain.model.RecipientEntity;
import com.futurotech.legal.domain.model.Template;
import com.futurotech.legal.domain.model.TenantConfig;
import com.futurotech.legal.domain.port.in.GenerateDocumentUseCase;
import com.futurotech.legal.domain.port.out.AIServicePort;
import com.futurotech.legal.domain.port.out.EntityRepositoryPort;
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
    /** Presupuesto total de caracteres para el texto de adjuntos del caso, para no
     *  exceder la ventana de contexto del modelo (evita el 413 de la API de IA). */
    private static final int ATTACHMENTS_CHAR_BUDGET = 24_000;
    private static final int MAX_STRUCTURE_LINES = 80;

    private final TenantRepositoryPort tenantRepo;
    private final TemplateRepositoryPort templateRepo;
    private final AIServicePort ai;
    private final FileExporterPort fileExporter;
    private final KnowledgeBasePort knowledgeBase;
    private final GeneratedDocumentPort auditTrail;
    private final EntityRepositoryPort entityRepo;
    private final CitationVerificationService citationVerification;
    private final DataVerificationService dataVerification;

    /**
     * Serializa las generaciones a {@code generation.max-concurrent} a la vez
     * (por defecto 1). Con Ollama on-premise el modelo compite por GPU/CPU y
     * varias generaciones simultáneas degradan o tumban el servicio; con una API
     * remota evita ráfagas que disparen límites de tasa. Las peticiones que no
     * obtienen permiso dentro del timeout fallan con un mensaje claro en vez de
     * encolarse indefinidamente.
     */
    private final Semaphore generationSlots;
    private final long acquireTimeoutSeconds;

    public GenerateDocumentService(TenantRepositoryPort tenantRepo, TemplateRepositoryPort templateRepo,
                                   @org.springframework.beans.factory.annotation.Qualifier("aiGenerator")
                                   AIServicePort ai, FileExporterPort fileExporter,
                                   KnowledgeBasePort knowledgeBase, GeneratedDocumentPort auditTrail,
                                   EntityRepositoryPort entityRepo,
                                   CitationVerificationService citationVerification,
                                   DataVerificationService dataVerification,
                                   @Value("${generation.max-concurrent:1}") int maxConcurrent,
                                   @Value("${generation.acquire-timeout-seconds:180}") long acquireTimeoutSeconds) {
        this.tenantRepo = tenantRepo;
        this.templateRepo = templateRepo;
        this.ai = ai;
        this.fileExporter = fileExporter;
        this.knowledgeBase = knowledgeBase;
        this.auditTrail = auditTrail;
        this.entityRepo = entityRepo;
        this.citationVerification = citationVerification;
        this.dataVerification = dataVerification;
        this.generationSlots = new Semaphore(Math.max(1, maxConcurrent), true);
        this.acquireTimeoutSeconds = acquireTimeoutSeconds;
    }

    @Override
    public GeneratedDocument generate(GenerateCommand cmd) {
        boolean acquired;
        try {
            acquired = generationSlots.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentGenerationException("La generación fue interrumpida. Intente de nuevo.");
        }
        if (!acquired) {
            log.warn("Generación rechazada: no hay cupo disponible tras {}s de espera", acquireTimeoutSeconds);
            throw new DocumentGenerationException(
                    "El servicio de generación está ocupado procesando otra solicitud. "
                    + "Espere a que termine e intente de nuevo.");
        }
        try {
            return doGenerate(cmd);
        } finally {
            generationSlots.release();
        }
    }

    private GeneratedDocument doGenerate(GenerateCommand cmd) {
        UUID transactionId = UUID.randomUUID();
        log.info("[{}] Inicio generación — tenant={} template={}", transactionId, cmd.tenantId(), cmd.templateId());

        TenantConfig tenant = tenantRepo.findById(cmd.tenantId())
                .orElseThrow(() -> new TenantNotFoundException(cmd.tenantId()));
        Template template = templateRepo.findById(cmd.templateId())
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla no encontrada: " + cmd.templateId()));

        String formatStructure = template.hasFormatDoc()
                ? extractFormatStructure(template.formatDocBytes()) : null;

        List<String> ragChunks = new ArrayList<>();
        StringBuilder facts = new StringBuilder();
        LegalContext context = composePrompt(tenant, template, cmd, formatStructure, ragChunks, facts);

        String rawText;
        try {
            rawText = ai.generate(context);
            log.info("[{}] IA completó — {} chars", transactionId, rawText.length());
        } catch (DocumentGenerationException e) {
            log.error("[{}] Falla de IA: {}", transactionId, e.getMessage());
            throw e;
        }

        // Verificación anti-alucinación (tolerante a fallos: no rompe la generación).
        Map<String, String> caseFields = new LinkedHashMap<>(cmd.caseFields());
        annotateVerification(transactionId, rawText, ragChunks,
                cleanMetadata(cmd.caseFields()), facts.toString(), caseFields);

        String fileName = transactionId + "_" + cmd.templateId() + ".docx";
        String saved = fileExporter.export(rawText, tenant.branding(), fileName, template.formatDocBytes());

        GeneratedDocument doc = new GeneratedDocument(
                transactionId, cmd.templateId(), null, cmd.authorUserId(), cmd.entityId(),
                caseFields, DocumentStatus.DRAFT, null, saved, sha256(rawText),
                Instant.now(), null, null, calculateDeadline(cmd.templateId()));
        auditTrail.save(doc);
        log.info("[{}] Documento registrado (DRAFT): {}", transactionId, saved);
        return doc;
    }

    /**
     * Ejecuta las verificaciones anti-alucinación sobre el borrador y anota el
     * resultado en caseFields con claves técnicas (prefijo "_", excluidas del
     * prompt). No lanza excepciones: la verificación es best-effort y la revisión
     * humana (DRAFT) es la salvaguarda final.
     */
    private void annotateVerification(UUID transactionId, String rawText, List<String> ragChunks,
                                      Map<String, String> caseData, String factsBlock,
                                      Map<String, String> caseFields) {
        // Verificador de citas (2º modelo).
        try {
            var citations = citationVerification.verify(rawText, ragChunks);
            storeResult(caseFields, "citations", citations);
            logResult(transactionId, "citas", citations);
        } catch (Exception e) {
            log.warn("[{}] Verificación de citas omitida: {}", transactionId, e.getMessage());
        }
        // Verificador de datos duros (3er modelo, familia distinta).
        try {
            var data = dataVerification.verify(rawText, caseData, factsBlock);
            storeResult(caseFields, "data", data);
            logResult(transactionId, "datos", data);
        } catch (Exception e) {
            log.warn("[{}] Verificación de datos omitida: {}", transactionId, e.getMessage());
        }
    }

    /** Persiste el resultado de una verificación en caseFields con claves técnicas "_". */
    private static void storeResult(Map<String, String> caseFields, String role,
                                    com.futurotech.legal.domain.model.VerificationResult r) {
        caseFields.put("_verification_" + role + "_passed", String.valueOf(r.passed()));
        if (!r.findings().isEmpty()) {
            caseFields.put("_verification_" + role + "_findings", String.join(" | ", r.findings()));
        }
        if (r.summary() != null && !r.summary().isBlank()) {
            caseFields.put("_verification_" + role + "_summary", r.summary());
        }
    }

    private void logResult(UUID transactionId, String label,
                           com.futurotech.legal.domain.model.VerificationResult r) {
        if (!r.passed()) {
            log.warn("[{}] Verificación de {} con hallazgos: {}", transactionId, label, r.findings());
        } else {
            log.info("[{}] Verificación de {} OK", transactionId, label);
        }
    }

    // --- Cálculo de plazos legales ---

    /**
     * Calcula el plazo legal de respuesta en días hábiles según el tipo de
     * plantilla. Petición general/pensional = 15 días hábiles, tutela = 10.
     * Si no se reconoce el tipo, usa 15 por defecto.
     */
    static Instant calculateDeadline(String templateId) {
        int businessDays = 15;
        if (templateId != null && templateId.contains("TUTELA")) {
            businessDays = 10;
        }
        return addBusinessDays(Instant.now(), businessDays);
    }

    /** Suma N días hábiles (lun-vie) a un Instant. No cuenta sábados ni domingos. */
    static Instant addBusinessDays(Instant start, int days) {
        java.time.ZonedDateTime zdt = start.atZone(java.time.ZoneId.of("America/Bogota"));
        int added = 0;
        while (added < days) {
            zdt = zdt.plusDays(1);
            if (zdt.getDayOfWeek().getValue() <= 5) { // lunes=1 .. viernes=5
                added++;
            }
        }
        return zdt.toInstant();
    }

    // --- Composición de prompt + RAG estricto (porta la lógica del sistema Python) ---

    private LegalContext composePrompt(TenantConfig tenant, Template template,
                                       GenerateCommand cmd, String formatStructure,
                                       List<String> ragChunksOut, StringBuilder factsOut) {
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

        // Documentos aportados del caso (hechos). Se calcula antes para poder aclarar
        // en el bloque de modo estricto que la restricción no aplica a estos hechos.
        String attachmentsBlock = buildAttachmentsBlock(cmd.caseAttachments());
        boolean hasAttachments = !attachmentsBlock.isEmpty();
        if (hasAttachments && factsOut != null) {
            factsOut.append(attachmentsBlock);
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
                ragChunksOut.addAll(chunks);
                // Numerar las fuentes para permitir citas inline trazables [Fuente N].
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < chunks.size(); i++) {
                    if (i > 0) {
                        sb.append("\n\n");
                    }
                    sb.append("[Fuente ").append(i + 1).append("]\n").append(chunks.get(i));
                }
                ragContext = sb.toString();
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
                        .append("respaldo en los fragmentos, NO lo incluyas. ")
                        .append("Cada fragmento está numerado como [Fuente N]: cuando cites una norma, añade al ")
                        .append("final de la frase la referencia a la fuente usada, p. ej. '(Fuente 2)'. Esto ")
                        .append("permite verificar la trazabilidad; no inventes números de fuente.")
                        .append(hasAttachments
                                ? " ESTA RESTRICCIÓN APLICA SOLO A NORMATIVA (leyes, decretos, jurisprudencia). "
                                + "Los HECHOS del caso provienen de los documentos aportados (más abajo) y DEBES usarlos."
                                : "")
                        .append("\n\n")
                        .append(ragContext).append("\n=== FIN DE FUENTES AUTORIZADAS ===");
            } else {
                systemPrompt.append("\n\nINSTRUCCIÓN ABSOLUTA — MODO ESTRICTO: ")
                        .append(hasIndexedDocs
                                ? "Los documentos de referencia no contienen fragmentos relevantes para este caso. "
                                : "No hay documentos de referencia cargados para esta plantilla. ")
                        .append("Queda PROHIBIDO citar, transcribir o mencionar el contenido de cualquier ")
                        .append("artículo, ley, decreto o sentencia. Puedes mencionar los nombres de las normas ")
                        .append("listadas en las reglas jurídicas, pero NUNCA su texto. Donde iría una cita ")
                        .append("textual escribe exactamente: [texto no disponible en fuentes autorizadas].")
                        .append(hasAttachments
                                ? " NOTA: esta prohibición es solo para NORMATIVA. Los HECHOS del caso "
                                + "provienen de los documentos aportados (más abajo) y DEBES incorporarlos."
                                : "");
            }
        } else if (!ragContext.isEmpty()) {
            systemPrompt.append("\n\nDocumentos de referencia (prioriza estas fuentes, puedes complementar ")
                    .append("con tu conocimiento jurídico):\n\n").append(ragContext);
        }

        // Documentos aportados del caso (hechos). Se inyectan SIEMPRE, al margen del
        // modo estricto (el bloque y hasAttachments se calcularon arriba).
        if (hasAttachments) {
            systemPrompt.append("\n\n=== DOCUMENTOS APORTADOS DEL CASO (HECHOS — USO OBLIGATORIO) ===\n")
                    .append("INSTRUCCIÓN ABSOLUTA: El usuario adjuntó los documentos de abajo, que son los ")
                    .append("HECHOS REALES de este caso concreto (p. ej. el derecho de petición negado que ")
                    .append("motiva una tutela). DEBES basar la narración de hechos del documento en su ")
                    .append("contenido: extrae y usa fechas, nombres de entidades, números de radicado/orden, ")
                    .append("pretensiones, lo solicitado y lo respondido/negado, y referéncialos con precisión. ")
                    .append("Estos hechos NO están sujetos a la restricción de fuentes normativas: son prueba ")
                    .append("aportada por la parte y su uso es obligatorio. La restricción del modo estricto ")
                    .append("aplica SOLO a citas de leyes, decretos y jurisprudencia, NO a estos hechos. ")
                    .append("No inventes hechos que no aparezcan en los documentos.\n\n")
                    .append(attachmentsBlock)
                    .append("\n=== FIN DE DOCUMENTOS APORTADOS ===");
            log.info("Adjuntos del caso: {} documento(s) inyectado(s) en el prompt",
                    cmd.caseAttachments().size());
        }

        Map<String, String> clean = cleanMetadata(cmd.caseFields());

        // Destinatario: el entityId seleccionado en el directorio DEBE llegar al modelo.
        // Si no se resuelve aquí, el documento ignora a quién va dirigido (lo inventa).
        RecipientEntity recipient = resolveRecipient(cmd.entityId());
        if (recipient != null) {
            // Volcar nombre/ciudad en los caseFields que esperan las plantillas (snake_case)
            // cuando el usuario no los escribió a mano, sin pisar un valor ya provisto.
            clean.putIfAbsent("entidad_destinataria", recipient.name());
            if (recipient.city() != null && !recipient.city().isBlank()) {
                clean.putIfAbsent("ciudad", recipient.city());
            }
            String destinatarioBlock = buildRecipientBlock(recipient);
            systemPrompt.append("\n\n=== DESTINATARIO DEL DOCUMENTO (USO OBLIGATORIO) ===\n")
                    .append("INSTRUCCIÓN ABSOLUTA: Este documento va dirigido EXCLUSIVAMENTE a la entidad ")
                    .append("indicada abajo. Úsala como destinatario en el encabezado ('Señores' + nombre ")
                    .append("en MAYÚSCULAS) y en todo el cuerpo. QUEDA PROHIBIDO dirigir el documento a ")
                    .append("cualquier otra entidad o inventar un destinatario distinto.\n\n")
                    .append(destinatarioBlock)
                    .append("\n=== FIN DE DESTINATARIO ===");
        }

        String userPrompt = "Genera el documento con plantilla '" + template.templateId()
                + "' usando estos datos del caso:\n" + clean;
        if (recipient != null) {
            userPrompt += "\n\nIMPORTANTE: El documento va dirigido a '" + recipient.name()
                    + "'. Usa exactamente esa entidad como destinatario; no la cambies por otra.";
        }
        if (hasAttachments) {
            userPrompt += "\n\nIMPORTANTE: Incorpora los HECHOS de los documentos aportados del caso "
                    + "(sección 'DOCUMENTOS APORTADOS DEL CASO') en la narración de hechos y la "
                    + "fundamentación, citando datos concretos (fechas, entidades, radicados, lo pedido "
                    + "y lo negado).";
        }

        return LegalContext.of(systemPrompt.toString(), userPrompt, clean);
    }

    /**
     * Resuelve la entidad destinataria a partir del entityId del comando. Devuelve
     * {@code null} si no hay entityId o no existe la entidad (p. ej. destinatario
     * en texto libre vía caseFields), sin romper la generación.
     */
    private RecipientEntity resolveRecipient(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return null;
        }
        return entityRepo.findById(entityId).orElseGet(() -> {
            log.warn("entityId '{}' no corresponde a ninguna entidad; se omite del prompt", entityId);
            return null;
        });
    }

    /** Arma el bloque de datos del destinatario (solo campos con valor). */
    private static String buildRecipientBlock(RecipientEntity e) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, "Nombre", e.name());
        appendField(sb, "NIT", e.nit());
        appendField(sb, "Representante legal", e.legalRep());
        appendField(sb, "Dirección", e.address());
        appendField(sb, "Ciudad", e.city());
        appendField(sb, "Tipo de entidad", e.entityType());
        return sb.toString().strip();
    }

    private static void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value.strip()).append("\n");
        }
    }

    /**
     * Concatena el texto de los adjuntos del caso (cada uno con su nombre como
     * encabezado), repartiendo un presupuesto de caracteres entre ellos y truncando
     * de forma ordenada. Evita exceder la ventana de contexto del modelo (413).
     */
    private static String buildAttachmentsBlock(List<CaseAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        List<CaseAttachment> withText = attachments.stream()
                .filter(a -> a != null && a.hasText())
                .toList();
        if (withText.isEmpty()) {
            return "";
        }
        // Presupuesto equitativo por documento (mínimo 500 chars cada uno).
        int perDoc = Math.max(500, ATTACHMENTS_CHAR_BUDGET / withText.size());

        StringBuilder sb = new StringBuilder();
        for (CaseAttachment att : withText) {
            if (sb.length() > 0) {
                sb.append("\n\n---\n\n");
            }
            String text = att.extractedText();
            boolean truncated = text.length() > perDoc;
            if (truncated) {
                text = text.substring(0, perDoc);
            }
            sb.append("[DOCUMENTO: ").append(att.fileName()).append("]\n").append(text);
            if (truncated) {
                sb.append("\n[... documento truncado por longitud ...]");
            }
        }
        if (sb.length() > 0) {
            log.info("Adjuntos: bloque de {} chars (presupuesto {} por documento)",
                    sb.length(), perDoc);
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
