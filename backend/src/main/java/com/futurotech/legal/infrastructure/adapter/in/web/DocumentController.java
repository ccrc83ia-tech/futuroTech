package com.futurotech.legal.infrastructure.adapter.in.web;

import com.futurotech.legal.application.service.CompanyService;
import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.DocumentStatus;
import com.futurotech.legal.domain.model.GeneratedDocument;
import com.futurotech.legal.domain.port.in.GenerateDocumentUseCase;
import com.futurotech.legal.domain.port.in.GenerateDocumentUseCase.GenerateCommand;
import com.futurotech.legal.domain.port.out.GeneratedDocumentPort;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.DocumentDtos.GenerateRequest;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.DocumentDtos.GeneratedDocResponse;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.DocumentDtos.ReviewRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final GenerateDocumentUseCase generateUseCase;
    private final GeneratedDocumentPort auditTrail;
    private final CompanyService companyService;
    private final Path outputDir;

    public DocumentController(GenerateDocumentUseCase generateUseCase, GeneratedDocumentPort auditTrail,
                              CompanyService companyService,
                              @Value("${docs.output-dir:generated_docs}") String outputDir) {
        this.generateUseCase = generateUseCase;
        this.auditTrail = auditTrail;
        this.companyService = companyService;
        this.outputDir = Path.of(outputDir);
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('PERM_generate_document')")
    public GeneratedDocResponse generate(@Valid @RequestBody GenerateRequest req, Authentication auth) {
        String tenantId = companyService.getActive()
                .orElseThrow(() -> new DocumentGenerationException("No hay una firma configurada"))
                .tenantId();
        boolean ragStrict = req.ragStrict() == null || req.ragStrict();

        var command = new GenerateCommand(
                req.templateId(), tenantId, auth.getName(), req.entityId(),
                req.caseFields() == null ? Map.of() : req.caseFields(),
                req.selectedRules(), ragStrict);

        GeneratedDocument doc = generateUseCase.generate(command);
        return toResponse(doc);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('PERM_generate_document')")
    public List<GeneratedDocResponse> myDocuments(Authentication auth) {
        return auditTrail.findByAuthor(auth.getName()).stream().map(DocumentController::toResponse).toList();
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('PERM_generate_document')")
    public ResponseEntity<Resource> download(@PathVariable UUID id) throws IOException {
        GeneratedDocument doc = auditTrail.findById(id)
                .orElseThrow(() -> new DocumentGenerationException("Documento no encontrado"));
        Path file = outputDir.resolve(doc.fileName());
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = Files.readAllBytes(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.fileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(new ByteArrayResource(bytes));
    }

    /** Transición de estado de revisión. Solo abogado o admin pueden revisar/aprobar. */
    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ABOGADO', 'ADMIN')")
    public GeneratedDocResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest req,
                                       Authentication auth) {
        DocumentStatus status = DocumentStatus.valueOf(req.status());
        auditTrail.updateStatus(id, status, auth.getName());
        return toResponse(auditTrail.findById(id).orElseThrow());
    }

    private static GeneratedDocResponse toResponse(GeneratedDocument d) {
        return new GeneratedDocResponse(
                d.id().toString(), d.templateId(), d.status().name(), d.fileName(),
                "/api/v1/documents/" + d.id() + "/download", d.createdAt());
    }
}
