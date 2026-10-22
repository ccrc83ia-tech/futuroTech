package com.futurotech.legal.infrastructure.adapter.in.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import com.futurotech.legal.application.service.CompanyService;
import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.exception.ResourceNotFoundException;
import com.futurotech.legal.domain.model.Branding;
import com.futurotech.legal.domain.model.CaseAttachment;
import com.futurotech.legal.domain.model.DocumentStatus;
import com.futurotech.legal.domain.model.GeneratedDocument;
import com.futurotech.legal.domain.model.TenantConfig;
import com.futurotech.legal.domain.port.in.GenerateDocumentUseCase;
import com.futurotech.legal.domain.port.in.GenerateDocumentUseCase.GenerateCommand;
import com.futurotech.legal.domain.port.out.EntityRepositoryPort;
import com.futurotech.legal.domain.port.out.GeneratedDocumentPort;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.DocumentDtos.GenerateRequest;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.DocumentDtos.GeneratedDocResponse;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.DocumentDtos.ReviewRequest;
import com.futurotech.legal.infrastructure.adapter.out.export.DocxWatermarkStamper;
import com.futurotech.legal.infrastructure.adapter.out.export.PdfExporter;
import com.futurotech.legal.infrastructure.extract.CaseDocumentExtractor;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    /** Texto de la marca de agua para documentos en borrador. */
    private static final String DRAFT_WATERMARK = "BORRADOR";

    private final GenerateDocumentUseCase generateUseCase;
    private final GeneratedDocumentPort auditTrail;
    private final CompanyService companyService;
    private final CaseDocumentExtractor caseDocumentExtractor;
    private final PdfExporter pdfExporter;
    private final DocxWatermarkStamper watermarkStamper;
    private final EntityRepositoryPort entityRepo;
    private final Path outputDir;

    public DocumentController(GenerateDocumentUseCase generateUseCase, GeneratedDocumentPort auditTrail,
                              CompanyService companyService, CaseDocumentExtractor caseDocumentExtractor,
                              PdfExporter pdfExporter, DocxWatermarkStamper watermarkStamper,
                              EntityRepositoryPort entityRepo,
                              @Value("${docs.output-dir:generated_docs}") String outputDir) {
        this.generateUseCase = generateUseCase;
        this.auditTrail = auditTrail;
        this.companyService = companyService;
        this.caseDocumentExtractor = caseDocumentExtractor;
        this.pdfExporter = pdfExporter;
        this.watermarkStamper = watermarkStamper;
        this.entityRepo = entityRepo;
        this.outputDir = Path.of(outputDir);
    }

    /**
     * Genera un documento. Acepta multipart/form-data: la parte {@code request}
     * es el JSON con los datos del caso, y la parte opcional {@code attachments}
     * son documentos del caso (PDF/DOCX/TXT) cuyo texto se inyecta como hechos.
     * Ejemplo: adjuntar el derecho de petición negado al crear una tutela.
     */
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_generate_document')")
    public GeneratedDocResponse generate(
            @Valid @RequestPart("request") GenerateRequest req,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments,
            Authentication auth) {
        String tenantId = companyService.getActive()
                .orElseThrow(() -> new DocumentGenerationException("No hay una firma configurada"))
                .tenantId();
        boolean ragStrict = req.ragStrict() == null || req.ragStrict();

        // Extrae el texto de los adjuntos del caso (si los hay).
        List<CaseAttachment> caseAttachments = new ArrayList<>();
        if (attachments != null) {
            for (MultipartFile file : attachments) {
                if (file != null && !file.isEmpty()) {
                    caseAttachments.add(caseDocumentExtractor.extract(file));
                }
            }
        }

        // Trazabilidad: deja constancia de los nombres de adjuntos en la auditoría.
        // La clave con prefijo "_" se excluye del prompt pero se persiste en case_fields.
        Map<String, String> caseFields = new LinkedHashMap<>(
                req.caseFields() == null ? Map.of() : req.caseFields());
        if (!caseAttachments.isEmpty()) {
            String names = caseAttachments.stream().map(CaseAttachment::fileName)
                    .reduce((a, b) -> a + ", " + b).orElse("");
            caseFields.put("_adjuntos", names);
        }

        var command = new GenerateCommand(
                req.templateId(), tenantId, auth.getName(), req.entityId(),
                caseFields, req.selectedRules(), ragStrict, caseAttachments);

        GeneratedDocument doc = generateUseCase.generate(command);
        return toResponse(doc);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('PERM_generate_document')")
    public List<GeneratedDocResponse> myDocuments(Authentication auth) {
        return auditTrail.findByAuthor(auth.getName()).stream().map(this::toResponse).toList();
    }

    /**
     * Descarga el documento. Por defecto en .docx (el archivo generado tal cual).
     * Con {@code ?format=pdf} se convierte el .docx a PDF al vuelo, aplicando el
     * membrete de la firma, sin volver a invocar a la IA ni guardar otro archivo.
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('PERM_generate_document')")
    public ResponseEntity<Resource> download(@PathVariable UUID id,
                                             @RequestParam(value = "format", defaultValue = "docx") String format)
            throws IOException {
        GeneratedDocument doc = auditTrail.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));
        Path file = outputDir.resolve(doc.fileName());
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        byte[] docxBytes = Files.readAllBytes(file);

        // Salvaguarda jurídica: mientras el documento esté en BORRADOR se estampa
        // una marca de agua "BORRADOR" en la descarga (DOCX y PDF). Al aprobarse
        // (APPROVED) se descarga limpio. La marca se aplica al vuelo según el
        // estado actual, no se persiste en el archivo del disco.
        String watermark = doc.status() == DocumentStatus.DRAFT ? DRAFT_WATERMARK : null;

        if ("pdf".equalsIgnoreCase(format)) {
            Branding branding = companyService.getActive().map(TenantConfig::branding).orElse(null);
            byte[] pdf = pdfExporter.buildFromDocx(docxBytes, branding, watermark);
            String pdfName = stripExtension(doc.fileName()) + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdfName + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new ByteArrayResource(pdf));
        }

        byte[] body = watermark != null ? watermarkStamper.stamp(docxBytes, watermark) : docxBytes;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.fileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(new ByteArrayResource(body));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Transición de estado de revisión. Solo abogado o admin pueden revisar/aprobar. */
    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ABOGADO', 'ADMIN')")
    public GeneratedDocResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest req,
                                       Authentication auth) {
        DocumentStatus status = DocumentStatus.valueOf(req.status());
        auditTrail.updateStatus(id, status, auth.getName());
        return toResponse(auditTrail.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado")));
    }

    /** Lista todos los documentos (admin y abogado ven los de todos los usuarios). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ABOGADO')")
    public List<GeneratedDocResponse> all() {
        return auditTrail.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Devuelve el texto del DOCX con marcado Markdown básico (negrillas, saltos
     * de línea). Permite que el frontend muestre un editor de texto antes de
     * descargar. La marca de agua BORRADOR se inyecta si el documento está en DRAFT.
     */
    @GetMapping("/{id}/text")
    @PreAuthorize("hasAuthority('PERM_generate_document')")
    public ResponseEntity<Map<String, String>> text(@PathVariable UUID id) throws IOException {
        GeneratedDocument doc = auditTrail.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));
        Path file = outputDir.resolve(doc.fileName());
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        byte[] docxBytes = Files.readAllBytes(file);
        String text = extractMarkdown(docxBytes);
        if (doc.status() == DocumentStatus.DRAFT) {
            text = "⚠️ BORRADOR — Requiere revisión de un abogado antes de su uso.\n\n" + text;
        }
        return ResponseEntity.ok(Map.of("text", text, "status", doc.status().name()));
    }

    /** Extrae el texto del DOCX preservando negrillas (**texto**) e itálicas (*texto*). */
    private static String extractMarkdown(byte[] docxBytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                if (para.getText().isBlank()) {
                    sb.append("\n");
                    continue;
                }
                for (XWPFRun run : para.getRuns()) {
                    String t = run.getText(0);
                    if (t == null || t.isEmpty()) continue;
                    boolean bold   = Boolean.TRUE.equals(run.isBold());
                    boolean italic = Boolean.TRUE.equals(run.isItalic());
                    if (bold && italic) t = "***" + t + "***";
                    else if (bold)      t = "**" + t + "**";
                    else if (italic)    t = "*" + t + "*";
                    sb.append(t);
                }
                sb.append("\n");
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return "";
        }
    }

    private GeneratedDocResponse toResponse(GeneratedDocument d) {
        String clientName = d.caseFields() != null
                ? d.caseFields().getOrDefault("cliente_nombre",
                  d.caseFields().getOrDefault("cliente", null))
                : null;
        String entityName = d.entityId() != null
                ? entityRepo.findById(d.entityId()).map(e -> e.name()).orElse(d.entityId())
                : null;
        return new GeneratedDocResponse(
                d.id().toString(), d.templateId(), d.status().name(), d.fileName(),
                "/api/v1/documents/" + d.id() + "/download", d.createdAt(),
                clientName, d.entityId(), entityName, d.authorUserId(),
                d.reviewedBy(), d.reviewedAt());
    }
}
