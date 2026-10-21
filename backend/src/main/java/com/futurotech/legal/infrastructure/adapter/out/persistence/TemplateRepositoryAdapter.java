package com.futurotech.legal.infrastructure.adapter.out.persistence;

import com.futurotech.legal.domain.model.Template;
import com.futurotech.legal.domain.port.out.TemplateRepositoryPort;
import com.futurotech.legal.infrastructure.adapter.out.persistence.entity.TemplateEntity;
import com.futurotech.legal.infrastructure.adapter.out.persistence.repository.TemplateJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de persistencia de plantillas: mapea TemplateEntity <-> Template. */
@Component
public class TemplateRepositoryAdapter implements TemplateRepositoryPort {

    private final TemplateJpaRepository jpa;

    public TemplateRepositoryAdapter(TemplateJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Template> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Template> findById(String templateId) {
        return jpa.findById(templateId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void save(Template t) {
        jpa.save(new TemplateEntity(
                t.templateId(), t.name(), t.description(), t.fields(),
                t.systemPrompt(), t.legalRules(), t.ragStrict(),
                t.formatDocName(), t.formatDocBytes()));
    }

    @Override
    @Transactional
    public void deleteById(String templateId) {
        jpa.deleteById(templateId);
    }

    private Template toDomain(TemplateEntity e) {
        return new Template(
                e.getTemplateId(), e.getName(), e.getDescription(), e.getFields(),
                e.getSystemPrompt(), e.getLegalRules(), e.isRagStrict(),
                e.getFormatDocName(), e.getFormatDocBytes());
    }
}
