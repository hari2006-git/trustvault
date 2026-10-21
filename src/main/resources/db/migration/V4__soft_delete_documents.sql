ALTER TABLE documents
    ADD COLUMN deleted_at TIMESTAMPTZ NULL,
    ADD COLUMN deleted_by UUID NULL
        REFERENCES app_users(id);

CREATE INDEX idx_documents_active_owner_created
    ON documents (owner_id, created_at DESC)
    WHERE deleted_at IS NULL;