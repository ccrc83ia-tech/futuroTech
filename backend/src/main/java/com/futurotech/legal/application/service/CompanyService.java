package com.futurotech.legal.application.service;

import com.futurotech.legal.domain.exception.TenantNotFoundException;
import com.futurotech.legal.domain.model.TenantConfig;
import com.futurotech.legal.domain.port.out.TenantRepositoryPort;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de la firma (company). MVP single-tenant: opera sobre el tenant
 * activo (la única fila esperada), pero el modelo soporta varios.
 */
@Service
public class CompanyService {

    private final TenantRepositoryPort tenantRepo;

    public CompanyService(TenantRepositoryPort tenantRepo) {
        this.tenantRepo = tenantRepo;
    }

    /** Devuelve la firma activa (la primera activa encontrada). */
    public Optional<TenantConfig> getActive() {
        return tenantRepo.findAll().stream().filter(TenantConfig::active).findFirst();
    }

    public TenantConfig getById(String tenantId) {
        return tenantRepo.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    public void save(TenantConfig tenant) {
        tenantRepo.save(tenant);
    }
}
