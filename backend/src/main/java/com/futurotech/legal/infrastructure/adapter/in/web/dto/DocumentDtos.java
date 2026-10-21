package com.futurotech.legal.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
            Instant createdAt
    ) {
    }

    public record ReviewRequest(
            @NotBlank String status      // REVIEWED | APPROVED | VOID
    ) {
    }
}
