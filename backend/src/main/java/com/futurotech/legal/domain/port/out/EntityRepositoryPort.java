package com.futurotech.legal.domain.port.out;

import com.futurotech.legal.domain.model.RecipientEntity;

import java.util.List;
import java.util.Optional;

/** Persistencia de entidades destinatarias. */
public interface EntityRepositoryPort {

    List<RecipientEntity> findAll();

    Optional<RecipientEntity> findById(String entityId);

    void save(RecipientEntity entity);

    void deleteById(String entityId);
}
