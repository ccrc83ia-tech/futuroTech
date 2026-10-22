package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "generated_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "template_id")
    private String templateId;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "author_user_id")
    private String authorUserId;

    @Column(name = "entity_id")
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "case_fields")
    private Map<String, String> caseFields;

    private String status;

    @Column(name = "internal_docket")
    private String internalDocket;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "deadline")
    private Instant deadline;
}
