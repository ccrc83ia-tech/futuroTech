package com.futurotech.legal.infrastructure.adapter.out.persistence;

import com.futurotech.legal.domain.model.TenantConfig;
import com.futurotech.legal.domain.port.out.TenantRepositoryPort;
import com.futurotech.legal.infrastructure.adapter.out.persistence.entity.TenantEntity;
import com.futurotech.legal.infrastructure.adapter.out.persistence.repository.TenantJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de persistencia de tenants: mapea TenantEntity <-> TenantConfig. */
@Component
public class TenantRepositoryAdapter implements TenantRepositoryPort {

    private final TenantJpaRepository jpa;

    public TenantRepositoryAdapter(TenantJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantConfig> findById(String tenantId) {
        return jpa.findById(tenantId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantConfig> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void save(TenantConfig t) {
        jpa.save(new TenantEntity(
                t.tenantId(), t.name(), t.systemPrompt(),
                t.legalRules(), t.branding(), t.active()));
    }

    @Override
    @Transactional
    public void deleteById(String tenantId) {
        jpa.deleteById(tenantId);
    }

    private TenantConfig toDomain(TenantEntity e) {
        return new TenantConfig(
                e.getTenantId(), e.getName(), e.getSystemPrompt(),
                e.getLegalRules(), e.getBranding(), e.isActive());
    }
}
