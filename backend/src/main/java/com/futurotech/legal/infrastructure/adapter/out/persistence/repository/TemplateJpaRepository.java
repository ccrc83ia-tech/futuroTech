package com.futurotech.legal.infrastructure.adapter.out.persistence.repository;

import com.futurotech.legal.infrastructure.adapter.out.persistence.entity.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateJpaRepository extends JpaRepository<TemplateEntity, String> {
}
