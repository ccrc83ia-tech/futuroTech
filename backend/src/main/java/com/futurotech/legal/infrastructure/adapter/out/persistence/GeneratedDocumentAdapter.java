package com.futurotech.legal.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.futurotech.legal.domain.exception.ResourceNotFoundException;
import com.futurotech.legal.domain.model.DocumentStatus;
import com.futurotech.legal.domain.model.GeneratedDocument;
import com.futurotech.legal.domain.port.out.GeneratedDocumentPort;
import com.futurotech.legal.infrastructure.adapter.out.persistence.entity.GeneratedDocumentEntity;
import com.futurotech.legal.infrastructure.adapter.out.persistence.repository.GeneratedDocumentJpaRepository;

/** Adaptador de persistencia del registro de documentos generados (auditoría). */
@Component
public class GeneratedDocumentAdapter implements GeneratedDocumentPort {

    private final GeneratedDocumentJpaRepository jpa;

    public GeneratedDocumentAdapter(GeneratedDocumentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(GeneratedDocument d) {
        jpa.save(new GeneratedDocumentEntity(
                d.id(), d.templateId(), d.templateVersion(), d.authorUserId(), d.entityId(),
                d.caseFields(), d.status().name(), d.internalDocket(), d.fileName(), d.fileHash(),
                d.createdAt(), d.reviewedBy(), d.reviewedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GeneratedDocument> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GeneratedDocument> findByAuthor(String authorUserId) {
        return jpa.findByAuthorUserIdOrderByCreatedAtDesc(authorUserId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GeneratedDocument> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void updateStatus(UUID id, DocumentStatus status, String reviewedBy) {
        GeneratedDocumentEntity e = jpa.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado: " + id));
        e.setStatus(status.name());
        e.setReviewedBy(reviewedBy);
        e.setReviewedAt(Instant.now());
        jpa.save(e);
    }

    private GeneratedDocument toDomain(GeneratedDocumentEntity e) {
        return new GeneratedDocument(
                e.getId(), e.getTemplateId(), e.getTemplateVersion(), e.getAuthorUserId(), e.getEntityId(),
                e.getCaseFields(), DocumentStatus.valueOf(e.getStatus()), e.getInternalDocket(),
                e.getFileName(), e.getFileHash(), e.getCreatedAt(), e.getReviewedBy(), e.getReviewedAt());
    }
}
