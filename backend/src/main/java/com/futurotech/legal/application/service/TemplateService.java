package com.futurotech.legal.application.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.futurotech.legal.domain.exception.ResourceNotFoundException;
import com.futurotech.legal.domain.model.CaseAttachment;
import com.futurotech.legal.domain.model.Template;
import com.futurotech.legal.domain.port.out.KnowledgeBasePort;
import com.futurotech.legal.domain.port.out.TemplateRepositoryPort;
import com.futurotech.legal.infrastructure.extract.CaseDocumentExtractor;

/** Casos de uso de gestión de plantillas (incluye documento modelo .docx y corpus RAG). */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final TemplateRepositoryPort templateRepo;
    private final KnowledgeBasePort knowledgeBase;
    private final CaseDocumentExtractor extractor;

    public TemplateService(TemplateRepositoryPort templateRepo, KnowledgeBasePort knowledgeBase,
                           CaseDocumentExtractor extractor) {
        this.templateRepo = templateRepo;
        this.knowledgeBase = knowledgeBase;
        this.extractor = extractor;
    }

    public List<Template> findAll() {
        return templateRepo.findAll();
    }

    public Optional<Template> findById(String templateId) {
        return templateRepo.findById(templateId);
    }

    public void save(Template template) {
        templateRepo.save(template);
    }

    public void delete(String templateId) {
        templateRepo.deleteById(templateId);
        // Limpia los vectores RAG asociados para evitar huérfanos.
        knowledgeBase.deleteByTemplate(templateId);
    }

    /** Asocia el documento modelo (.docx) cuyo formato debe preservarse. */
    public void setFormatDoc(String templateId, String docName, byte[] bytes) {
        Template t = templateRepo.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla no encontrada: " + templateId));
        templateRepo.save(new Template(t.templateId(), t.name(), t.description(), t.fields(),
                t.systemPrompt(), t.legalRules(), t.ragStrict(), docName, bytes));
    }

    public void clearFormatDoc(String templateId) {
        Template t = templateRepo.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla no encontrada: " + templateId));
        templateRepo.save(new Template(t.templateId(), t.name(), t.description(), t.fields(),
                t.systemPrompt(), t.legalRules(), t.ragStrict(), null, null));
    }

    // --- Corpus RAG por plantilla (fuentes normativas autorizadas) ---

    /** Lista los nombres de documentos RAG indexados para una plantilla. */
    public List<String> listRagDocuments(String templateId) {
        requireTemplate(templateId);
        return knowledgeBase.listDocuments(templateId);
    }

    /**
     * Extrae el texto de un archivo (PDF/DOCX/TXT), lo fragmenta y lo indexa como
     * fuente normativa de la plantilla. Si ya existía un documento con el mismo
     * nombre, se reindexa (se borra el anterior primero). Devuelve el nº de chunks.
     */
    public int addRagDocument(String templateId, MultipartFile file) {
        requireTemplate(templateId);
        CaseAttachment extracted = extractor.extract(file);
        String docName = extracted.fileName();
        // Reindexado idempotente: evita chunks duplicados si se vuelve a subir.
        knowledgeBase.deleteDocument(templateId, docName);
        int chunks = knowledgeBase.ingest(templateId, docName, extracted.extractedText());
        log.info("RAG doc '{}' indexado en plantilla '{}' ({} chunks)", docName, templateId, chunks);
        return chunks;
    }

    /** Elimina un documento RAG (todos sus chunks) de una plantilla. */
    public void deleteRagDocument(String templateId, String docName) {
        requireTemplate(templateId);
        knowledgeBase.deleteDocument(templateId, docName);
        log.info("RAG doc '{}' eliminado de plantilla '{}'", docName, templateId);
    }

    private void requireTemplate(String templateId) {
        if (templateRepo.findById(templateId).isEmpty()) {
            throw new ResourceNotFoundException("Plantilla no encontrada: " + templateId);
        }
    }
}
