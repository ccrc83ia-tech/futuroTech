package com.futurotech.legal.infrastructure.adapter.out.persistence.repository;

import com.futurotech.legal.infrastructure.adapter.out.persistence.entity.RecipientEntityJpa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityJpaRepository extends JpaRepository<RecipientEntityJpa, String> {
}
