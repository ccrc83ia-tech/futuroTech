package com.futurotech.legal.infrastructure.adapter.in.web;

import com.futurotech.legal.application.service.CompanyService;
import com.futurotech.legal.domain.model.TenantConfig;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.CompanyDtos.CompanyResponse;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.CompanyDtos.UpdateCompanyRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de la firma (company) y su branding. MVP single-tenant. */
@RestController
@RequestMapping("/api/v1/company")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_view_company')")
    public ResponseEntity<CompanyResponse> get() {
        return companyService.getActive()
                .map(CompanyController::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_manage_company')")
    public CompanyResponse update(@Valid @RequestBody UpdateCompanyRequest req) {
        var tenant = new TenantConfig(req.tenantId(), req.name(), req.systemPrompt(),
                req.legalRules(), req.branding(), true);
        companyService.save(tenant);
        return toResponse(tenant);
    }

    private static CompanyResponse toResponse(TenantConfig t) {
        return new CompanyResponse(t.tenantId(), t.name(), t.systemPrompt(),
                t.legalRules(), t.branding(), t.active());
    }
}
