package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

import com.futurotech.legal.domain.model.Branding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantEntity {

    @Id
    @Column(name = "tenant_id")
    private String tenantId;

    private String name;

    @Column(name = "system_prompt")
    private String systemPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "legal_rules")
    private List<String> legalRules;

    @JdbcTypeCode(SqlTypes.JSON)
    private Branding branding;

    private boolean active;
}
