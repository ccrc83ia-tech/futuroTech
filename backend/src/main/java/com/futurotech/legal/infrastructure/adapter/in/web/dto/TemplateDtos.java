package com.futurotech.legal.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

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
}
