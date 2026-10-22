package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

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
@Table(name = "templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
}
