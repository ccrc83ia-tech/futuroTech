package com.futurotech.legal.infrastructure.adapter.out.persistence.repository;

import com.futurotech.legal.infrastructure.adapter.out.persistence.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantJpaRepository extends JpaRepository<TenantEntity, String> {
}
