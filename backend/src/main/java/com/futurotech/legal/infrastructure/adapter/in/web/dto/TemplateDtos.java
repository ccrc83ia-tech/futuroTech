package com.futurotech.legal.infrastructure.adapter.in.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public final class TemplateDtos {

    private TemplateDtos() {
    }

    public record TemplateResponse(
            String templateId,
            String name,
            String description,
            List<String> fields,
            String systemPrompt,
            List<String> legalRules,
            boolean ragStrict,
            String formatDocName,
            boolean hasFormatDoc
    ) {
    }

    public record SaveTemplateRequest(
            @NotBlank String templateId,
            @NotBlank String name,
            String description,
            List<String> fields,
            String systemPrompt,
            List<String> legalRules,
            boolean ragStrict
    ) {
    }

    /** Resultado de indexar un documento RAG: nombre y nº de fragmentos generados. */
    public record RagDocumentResponse(
            String docName,
            int chunks
    ) {
    }
}
