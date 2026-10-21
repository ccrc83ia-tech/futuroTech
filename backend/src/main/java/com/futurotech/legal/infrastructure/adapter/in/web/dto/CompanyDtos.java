package com.futurotech.legal.infrastructure.adapter.in.web.dto;

import com.futurotech.legal.domain.model.Branding;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class CompanyDtos {

    private CompanyDtos() {
    }

    public record CompanyResponse(
            String tenantId,
            String name,
            String systemPrompt,
            List<String> legalRules,
            Branding branding,
            boolean active
    ) {
    }

    public record UpdateCompanyRequest(
            @NotBlank String tenantId,
            @NotBlank String name,
            String systemPrompt,
            List<String> legalRules,
            Branding branding
    ) {
    }
}
