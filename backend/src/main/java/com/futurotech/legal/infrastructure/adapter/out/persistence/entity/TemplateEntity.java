package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "templates")
public class TemplateEntity {

    @Id
    @Column(name = "template_id")
    private String templateId;

    private String name;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> fields;

    @Column(name = "system_prompt")
    private String systemPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "legal_rules")
    private List<String> legalRules;

    @Column(name = "rag_strict")
    private boolean ragStrict;

    @Column(name = "format_doc_name")
    private String formatDocName;

    @Column(name = "format_doc_bytes")
    private byte[] formatDocBytes;

    protected TemplateEntity() {
    }

    public TemplateEntity(String templateId, String name, String description, List<String> fields,
                          String systemPrompt, List<String> legalRules, boolean ragStrict,
                          String formatDocName, byte[] formatDocBytes) {
        this.templateId = templateId;
        this.name = name;
        this.description = description;
        this.fields = fields;
        this.systemPrompt = systemPrompt;
        this.legalRules = legalRules;
        this.ragStrict = ragStrict;
        this.formatDocName = formatDocName;
        this.formatDocBytes = formatDocBytes;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getFields() {
        return fields;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<String> getLegalRules() {
        return legalRules;
    }

    public boolean isRagStrict() {
        return ragStrict;
    }

    public String getFormatDocName() {
        return formatDocName;
    }

    public byte[] getFormatDocBytes() {
        return formatDocBytes;
    }
}
