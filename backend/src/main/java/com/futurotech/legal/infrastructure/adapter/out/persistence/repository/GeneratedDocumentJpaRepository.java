package com.futurotech.legal.infrastructure.adapter.out.persistence.repository;

import com.futurotech.legal.infrastructure.adapter.out.persistence.entity.GeneratedDocumentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedDocumentJpaRepository extends JpaRepository<GeneratedDocumentEntity, UUID> {
    List<GeneratedDocumentEntity> findByAuthorUserIdOrderByCreatedAtDesc(String authorUserId);
}
