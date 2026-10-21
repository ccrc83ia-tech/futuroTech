package com.futurotech.legal.application.service;

import com.futurotech.legal.domain.model.RecipientEntity;
import com.futurotech.legal.domain.port.out.EntityRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Casos de uso de gestión de entidades destinatarias. */
@Service
public class EntityService {

    private final EntityRepositoryPort entityRepo;

    public EntityService(EntityRepositoryPort entityRepo) {
        this.entityRepo = entityRepo;
    }

    public List<RecipientEntity> findAll() {
        return entityRepo.findAll();
    }

    public Optional<RecipientEntity> findById(String entityId) {
        return entityRepo.findById(entityId);
    }

    public void save(RecipientEntity entity) {
        entityRepo.save(entity);
    }

    public void delete(String entityId) {
        entityRepo.deleteById(entityId);
    }
}
