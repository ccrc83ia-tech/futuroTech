package com.futurotech.legal.domain.model;

import java.util.List;

/**
 * Configuración de la firma (tenant). Single-tenant en el MVP, pero el modelo
 * conserva el concepto para permitir multi-tenant en el futuro.
 */
public record TenantConfig(
        String tenantId,
        String name,
        String systemPrompt,
        List<String> legalRules,
        Branding branding,
        boolean active
) {
    public TenantConfig {
        legalRules = legalRules == null ? List.of() : List.copyOf(legalRules);
        branding = branding == null ? Branding.empty() : branding;
    }
}
