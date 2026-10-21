package com.futurotech.legal.infrastructure.adapter.out.persistence;

import com.futurotech.legal.domain.model.RecipientEntity;
import com.futurotech.legal.domain.port.out.EntityRepositoryPort;
import com.futurotech.legal.infrastructure.adapter.out.persistence.entity.RecipientEntityJpa;
import com.futurotech.legal.infrastructure.adapter.out.persistence.repository.EntityJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de persistencia de entidades destinatarias. */
@Component
public class EntityRepositoryAdapter implements EntityRepositoryPort {

    private final EntityJpaRepository jpa;

    public EntityRepositoryAdapter(EntityJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipientEntity> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecipientEntity> findById(String entityId) {
        return jpa.findById(entityId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void save(RecipientEntity e) {
        jpa.save(new RecipientEntityJpa(
                e.entityId(), e.name(), e.nit(), e.address(), e.city(),
                e.phone(), e.email(), e.legalRep(), e.entityType(), e.notes()));
    }

    @Override
    @Transactional
    public void deleteById(String entityId) {
        jpa.deleteById(entityId);
    }

    private RecipientEntity toDomain(RecipientEntityJpa e) {
        return new RecipientEntity(
                e.getEntityId(), e.getName(), e.getNit(), e.getAddress(), e.getCity(),
                e.getPhone(), e.getEmail(), e.getLegalRep(), e.getEntityType(), e.getNotes());
    }
}
