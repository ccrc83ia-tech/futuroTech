package com.futurotech.legal.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Registro de auditoría de un documento generado (trazabilidad).
 * Append-only: no se borra, a lo sumo se marca como VOID.
 */
public record GeneratedDocument(
        UUID id,
        String templateId,
        Integer templateVersion,
        String authorUserId,
        String entityId,
        Map<String, String> caseFields,
        DocumentStatus status,
        String internalDocket,
        String fileName,
        String fileHash,
        Instant createdAt,
        String reviewedBy,
        Instant reviewedAt,
        Instant deadline
) {
    public GeneratedDocument {
        caseFields = caseFields == null ? Map.of() : Map.copyOf(caseFields);
    }
}
