-- =====================================================================
-- FuturoTech AI — Esquema inicial
-- PostgreSQL + pgvector. MVP single-tenant (modelo multi-tenant-ready).
-- Dimensión de embeddings: 768 (Ollama nomic-embed-text).
--   Si se usa mxbai-embed-large -> cambiar a 1024.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- --- Firma / empresa (branding). Single-tenant: una sola fila en el MVP. ---
CREATE TABLE tenant (
    tenant_id      VARCHAR(64)  PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    system_prompt  TEXT         NOT NULL DEFAULT '',
    legal_rules    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    branding       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

-- --- Usuarios (RBAC) ---
CREATE TABLE users (
    user_id        VARCHAR(64)  PRIMARY KEY,
    username       VARCHAR(100) NOT NULL UNIQUE,
    full_name      VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NOT NULL DEFAULT '',
    role           VARCHAR(32)  NOT NULL DEFAULT 'pasante',
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    password_hash  VARCHAR(255) NOT NULL DEFAULT '',
    permissions    JSONB        DEFAULT NULL          -- null => permisos del rol
);

-- --- Plantillas de documento ---
CREATE TABLE templates (
    template_id      VARCHAR(64)  PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    description      TEXT         NOT NULL DEFAULT '',
    fields           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    system_prompt    TEXT         NOT NULL DEFAULT '',
    legal_rules      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    rag_strict       BOOLEAN      NOT NULL DEFAULT TRUE,
    format_doc_name  VARCHAR(255) DEFAULT NULL,
    format_doc_bytes BYTEA        DEFAULT NULL
);

-- --- Historial inmutable de versiones por plantilla ---
CREATE TABLE template_versions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id   VARCHAR(64)  NOT NULL,
    version       INTEGER      NOT NULL DEFAULT 1,
    name          VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL DEFAULT '',
    fields        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    system_prompt TEXT         NOT NULL DEFAULT '',
    legal_rules   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    UNIQUE (template_id, version)
);

-- --- Documentos RAG indexados por plantilla (metadatos) ---
CREATE TABLE template_documents (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id   VARCHAR(64)  NOT NULL REFERENCES templates(template_id) ON DELETE CASCADE,
    doc_name      VARCHAR(255) NOT NULL,
    file_type     VARCHAR(32)  NOT NULL DEFAULT '',
    chunks_count  INTEGER      NOT NULL DEFAULT 0,
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (template_id, doc_name)
);

-- --- Chunks vectoriales (RAG) ---
CREATE TABLE template_chunks (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id  VARCHAR(64)  NOT NULL REFERENCES templates(template_id) ON DELETE CASCADE,
    doc_name     VARCHAR(255) NOT NULL,
    chunk_index  INTEGER      NOT NULL,
    content      TEXT         NOT NULL,
    embedding    vector(768)  NOT NULL
);

CREATE INDEX idx_template_chunks_template ON template_chunks (template_id);
CREATE INDEX idx_template_chunks_embedding ON template_chunks
    USING hnsw (embedding vector_cosine_ops);

-- --- Entidades destinatarias ---
CREATE TABLE entities (
    entity_id     VARCHAR(64)  PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    nit           VARCHAR(64)  NOT NULL DEFAULT '',
    address       VARCHAR(512) NOT NULL DEFAULT '',
    city          VARCHAR(128) NOT NULL DEFAULT '',
    phone         VARCHAR(64)  NOT NULL DEFAULT '',
    email         VARCHAR(255) NOT NULL DEFAULT '',
    legal_rep     VARCHAR(255) NOT NULL DEFAULT '',
    entity_type   VARCHAR(64)  NOT NULL DEFAULT '',
    notes         TEXT         NOT NULL DEFAULT ''
);

-- --- Documentos generados (trazabilidad / auditoría). Append-only. ---
CREATE TABLE generated_document (
    id              UUID         PRIMARY KEY,
    template_id     VARCHAR(64)  NOT NULL,
    template_version INTEGER     DEFAULT NULL,
    author_user_id  VARCHAR(64)  NOT NULL,
    entity_id       VARCHAR(64)  DEFAULT NULL,
    case_fields     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',   -- DRAFT | REVIEWED | APPROVED | VOID
    internal_docket VARCHAR(64)  DEFAULT NULL,               -- radicado interno opcional
    file_name       VARCHAR(255) DEFAULT NULL,
    file_hash       VARCHAR(128) DEFAULT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_by     VARCHAR(64)  DEFAULT NULL,
    reviewed_at     TIMESTAMPTZ  DEFAULT NULL
);

CREATE INDEX idx_generated_document_author ON generated_document (author_user_id);
CREATE INDEX idx_generated_document_created ON generated_document (created_at);
