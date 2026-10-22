package com.futurotech.legal.infrastructure.adapter.in.web;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.futurotech.legal.application.service.TemplateService;
import com.futurotech.legal.domain.model.Template;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.TemplateDtos.RagDocumentResponse;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.TemplateDtos.SaveTemplateRequest;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.TemplateDtos.TemplateResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_view_templates')")
    public List<TemplateResponse> list() {
        return templateService.findAll().stream().map(TemplateController::toResponse).toList();
    }

    @GetMapping("/{templateId}")
    @PreAuthorize("hasAuthority('PERM_view_templates')")
    public ResponseEntity<TemplateResponse> get(@PathVariable String templateId) {
        return templateService.findById(templateId)
                .map(TemplateController::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_manage_templates')")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@Valid @RequestBody SaveTemplateRequest req) {
        return save(req);
    }

    @PutMapping("/{templateId}")
    @PreAuthorize("hasAuthority('PERM_manage_templates')")
    public TemplateResponse update(@PathVariable String templateId, @Valid @RequestBody SaveTemplateRequest req) {
        return save(req);
    }

    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasAuthority('PERM_manage_templates')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String templateId) {
        templateService.delete(templateId);
    }

    /** Sube el documento modelo (.docx) cuyo formato y estructura deben preservarse. */
    @PostMapping("/{templateId}/format-doc")
    @PreAuthorize("hasAuthority('PERM_manage_templates')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadFormatDoc(@PathVariable String templateId,
                                @RequestParam("file") MultipartFile file) throws IOException {
        templateService.setFormatDoc(templateId, file.getOriginalFilename(), file.getBytes());
    }

    @DeleteMapping("/{templateId}/format-doc")
    @PreAuthorize("hasAuthority('PERM_manage_templates')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFormatDoc(@PathVariable String templateId) {
        templateService.clearFormatDoc(templateId);
    }

    // --- Corpus RAG por plantilla (fuentes normativas autorizadas) ---

    /** Lista los documentos RAG (fuentes normativas) indexados para la plantilla. */
    @GetMapping("/{templateId}/documents")
    @PreAuthorize("hasAuthority('PERM_view_templates')")
    public List<String> listRagDocuments(@PathVariable String templateId) {
        return templateService.listRagDocuments(templateId);
    }

    /** Sube e indexa un documento (PDF/DOCX/TXT) como fuente normativa de la plantilla. */
    @PostMapping("/{templateId}/documents")
    @PreAuthorize("hasAuthority('PERM_manage_templates')")
    @ResponseStatus(HttpStatus.CREATED)
    public RagDocumentResponse uploadRagDocument(@PathVariable String templateId,
                                                 @RequestParam("file") MultipartFile file) {
        int chunks = templateService.addRagDocument(templateId, file);
        return new RagDocumentResponse(file.getOriginalFilename(), chunks);
    }

    /** Elimina un documento RAG (todos sus fragmentos) de la plantilla. */
    @DeleteMapping("/{templateId}/documents")
    @PreAuthorize("hasAuthority('PERM_manage_templates')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRagDocument(@PathVariable String templateId,
                                  @RequestParam("docName") String docName) {
        templateService.deleteRagDocument(templateId, docName);
    }

    private TemplateResponse save(SaveTemplateRequest req) {
        // Conserva el documento modelo existente si la plantilla ya lo tiene.
        Template existing = templateService.findById(req.templateId()).orElse(null);
        String formatName = existing != null ? existing.formatDocName() : null;
        byte[] formatBytes = existing != null ? existing.formatDocBytes() : null;

        Template t = new Template(req.templateId(), req.name(), req.description(),
                req.fields(), req.systemPrompt(), req.legalRules(), req.ragStrict(),
                formatName, formatBytes);
        templateService.save(t);
        return toResponse(t);
    }

    private static TemplateResponse toResponse(Template t) {
        return new TemplateResponse(t.templateId(), t.name(), t.description(), t.fields(),
                t.systemPrompt(), t.legalRules(), t.ragStrict(), t.formatDocName(), t.hasFormatDoc());
    }
}
