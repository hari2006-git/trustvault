CREATE TABLE document_versions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    checksum_sha256 VARCHAR(64),
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_version_number
        UNIQUE (document_id, version_number)
);

CREATE INDEX idx_document_versions_document_created
    ON document_versions (document_id, created_at DESC);
