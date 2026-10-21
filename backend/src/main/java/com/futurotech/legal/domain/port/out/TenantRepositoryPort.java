package com.futurotech.legal.domain.port.out;

import com.futurotech.legal.domain.model.TenantConfig;

import java.util.List;
import java.util.Optional;

/** Persistencia de la configuración de firmas (tenants). */
public interface TenantRepositoryPort {

    Optional<TenantConfig> findById(String tenantId);

    List<TenantConfig> findAll();

    void save(TenantConfig tenant);

    void deleteById(String tenantId);
}
