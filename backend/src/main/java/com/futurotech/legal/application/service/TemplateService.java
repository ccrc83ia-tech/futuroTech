package com.futurotech.legal.application.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.futurotech.legal.domain.exception.ResourceNotFoundException;
import com.futurotech.legal.domain.model.Template;
import com.futurotech.legal.domain.port.out.KnowledgeBasePort;
import com.futurotech.legal.domain.port.out.TemplateRepositoryPort;

/** Casos de uso de gestión de plantillas (incluye documento modelo .docx). */
@Service
public class TemplateService {

    private final TemplateRepositoryPort templateRepo;
    private final KnowledgeBasePort knowledgeBase;

    public TemplateService(TemplateRepositoryPort templateRepo, KnowledgeBasePort knowledgeBase) {
        this.templateRepo = templateRepo;
        this.knowledgeBase = knowledgeBase;
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
}
