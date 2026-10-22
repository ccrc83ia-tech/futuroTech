package com.futurotech.legal.infrastructure.adapter.in.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.futurotech.legal.domain.port.out.GeneratedDocumentPort;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.DocumentDtos.GenerateRequest;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.DocumentDtos.GeneratedDocResponse;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.DocumentDtos.ReviewRequest;
import com.futurotech.legal.infrastructure.adapter.out.export.PdfExporter;
import com.futurotech.legal.infrastructure.extract.CaseDocumentExtractor;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final GenerateDocumentUseCase generateUseCase;
    private final GeneratedDocumentPort auditTrail;
    private final CompanyService companyService;
    private final CaseDocumentExtractor caseDocumentExtractor;
    private final PdfExporter pdfExporter;
    private final Path outputDir;

    public DocumentController(GenerateDocumentUseCase generateUseCase, GeneratedDocumentPort auditTrail,
                              CompanyService companyService, CaseDocumentExtractor caseDocumentExtractor,
                              PdfExporter pdfExporter,
                              @Value("${docs.output-dir:generated_docs}") String outputDir) {
        this.generateUseCase = generateUseCase;
        this.auditTrail = auditTrail;
        this.companyService = companyService;
        this.caseDocumentExtractor = caseDocumentExtractor;
        this.pdfExporter = pdfExporter;
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
        return auditTrail.findByAuthor(auth.getName()).stream().map(DocumentController::toResponse).toList();
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

        if ("pdf".equalsIgnoreCase(format)) {
            Branding branding = companyService.getActive().map(TenantConfig::branding).orElse(null);
            byte[] pdf = pdfExporter.buildFromDocx(docxBytes, branding);
            String pdfName = stripExtension(doc.fileName()) + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdfName + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new ByteArrayResource(pdf));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.fileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(new ByteArrayResource(docxBytes));
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

    private static GeneratedDocResponse toResponse(GeneratedDocument d) {
        return new GeneratedDocResponse(
                d.id().toString(), d.templateId(), d.status().name(), d.fileName(),
                "/api/v1/documents/" + d.id() + "/download", d.createdAt());
    }
}
