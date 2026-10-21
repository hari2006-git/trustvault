
CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_app_users_email
        CHECK (LENGTH(TRIM(email)) > 0),

    CONSTRAINT chk_app_users_display_name
        CHECK (LENGTH(TRIM(display_name)) > 0)
);

CREATE UNIQUE INDEX uq_app_users_email_lower
    ON app_users (LOWER(email));

ALTER TABLE documents
    ADD COLUMN owner_id UUID NOT NULL;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_owner
    FOREIGN KEY (owner_id)
    REFERENCES app_users(id)
    ON DELETE RESTRICT;

CREATE INDEX idx_documents_owner_created_at
    ON documents (owner_id, created_at DESC);
