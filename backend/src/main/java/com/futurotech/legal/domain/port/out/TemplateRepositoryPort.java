package com.futurotech.legal.domain.port.out;

import com.futurotech.legal.domain.model.Template;

import java.util.List;
import java.util.Optional;

/** Persistencia de plantillas de documento. */
public interface TemplateRepositoryPort {

    List<Template> findAll();

    Optional<Template> findById(String templateId);

    void save(Template template);

    void deleteById(String templateId);
}
