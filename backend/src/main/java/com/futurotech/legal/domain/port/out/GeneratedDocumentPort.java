package com.futurotech.legal.domain.port.out;

import com.futurotech.legal.domain.model.GeneratedDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia del registro de auditoría de documentos generados (append-only). */
public interface GeneratedDocumentPort {

    void save(GeneratedDocument document);

    Optional<GeneratedDocument> findById(UUID id);

    List<GeneratedDocument> findByAuthor(String authorUserId);

    List<GeneratedDocument> findAll();

    /** Actualiza solo el estado de revisión (DRAFT -> REVIEWED/APPROVED/VOID). */
    void updateStatus(UUID id, com.futurotech.legal.domain.model.DocumentStatus status, String reviewedBy);
}
