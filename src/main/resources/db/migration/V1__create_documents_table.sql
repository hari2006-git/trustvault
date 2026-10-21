
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_documents_original_name
        CHECK (LENGTH(TRIM(original_name)) > 0),

    CONSTRAINT chk_documents_storage_key
        CHECK (LENGTH(TRIM(storage_key)) > 0),

    CONSTRAINT chk_documents_size_bytes
        CHECK (size_bytes >= 0),

    CONSTRAINT chk_documents_checksum_sha256
        CHECK (
            checksum_sha256 IS NULL
            OR checksum_sha256 ~ '^[0-9a-fA-F]{64}$'
        )
);

CREATE INDEX idx_documents_created_at
    ON documents (created_at DESC);
