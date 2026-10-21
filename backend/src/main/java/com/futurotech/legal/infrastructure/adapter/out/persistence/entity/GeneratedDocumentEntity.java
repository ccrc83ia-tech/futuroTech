package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "generated_document")
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

    protected GeneratedDocumentEntity() {
    }

    public GeneratedDocumentEntity(UUID id, String templateId, Integer templateVersion, String authorUserId,
                                   String entityId, Map<String, String> caseFields, String status,
                                   String internalDocket, String fileName, String fileHash, Instant createdAt,
                                   String reviewedBy, Instant reviewedAt) {
        this.id = id;
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.authorUserId = authorUserId;
        this.entityId = entityId;
        this.caseFields = caseFields;
        this.status = status;
        this.internalDocket = internalDocket;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.createdAt = createdAt;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTemplateId() {
        return templateId;
    }

    public Integer getTemplateVersion() {
        return templateVersion;
    }

    public String getAuthorUserId() {
        return authorUserId;
    }

    public String getEntityId() {
        return entityId;
    }

    public Map<String, String> getCaseFields() {
        return caseFields;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInternalDocket() {
        return internalDocket;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
