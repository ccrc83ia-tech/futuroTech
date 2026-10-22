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
            String clientName,
            String entityId,
            String entityName,
            String authorUserId,
            String reviewedBy,
            Instant reviewedAt,
            // --- Plazos y radicado ---
            Instant deadline,
            String internalDocket
    ) {
    }

    public record ReviewRequest(
            @NotBlank String status
    ) {
    }

    public record DocketRequest(
            @NotBlank String internalDocket
    ) {
    }

    public record StatsResponse(
            long totalMonth,
            long pendingReview,
            long upcomingDeadlines,
            long overdue,
            Map<String, Long> byTemplate
    ) {
    }
}
