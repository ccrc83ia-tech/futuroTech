package com.futurotech.legal.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record GenerateRequest(
            @NotBlank String templateId,
            String entityId,
            Map<String, String> caseFields,
            List<String> selectedRules,
            Boolean ragStrict
    ) {
    }

    public record GeneratedDocResponse(
            String transactionId,
            String templateId,
            String status,
            String fileName,
            String downloadUrl,
            Instant createdAt,
            // --- Campos de trazabilidad y contexto del caso ---
            String clientName,        // caseFields["cliente_nombre"] para identificar el caso
            String entityId,          // id de la entidad destinataria
            String entityName,        // nombre resuelto de la entidad destinataria
            String authorUserId,      // usuario que generó el documento
            String reviewedBy,        // usuario que lo aprobó/revisó (null si DRAFT)
            Instant reviewedAt        // fecha de aprobación/revisión
    ) {
    }

    public record ReviewRequest(
            @NotBlank String status      // REVIEWED | APPROVED | VOID
    ) {
    }
}
