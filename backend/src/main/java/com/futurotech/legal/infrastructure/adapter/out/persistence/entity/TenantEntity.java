package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

import com.futurotech.legal.domain.model.Branding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant")
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

    protected TenantEntity() {
    }

    public TenantEntity(String tenantId, String name, String systemPrompt,
                        List<String> legalRules, Branding branding, boolean active) {
        this.tenantId = tenantId;
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.legalRules = legalRules;
        this.branding = branding;
        this.active = active;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<String> getLegalRules() {
        return legalRules;
    }

    public Branding getBranding() {
        return branding;
    }

    public boolean isActive() {
        return active;
    }
}
