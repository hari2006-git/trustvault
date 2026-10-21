
-- Add application roles. Existing accounts remain document owners
-- by default; the runtime authorization policy will be enforced in Java.
ALTER TABLE app_users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'OWNER';

ALTER TABLE app_users
    ADD CONSTRAINT chk_app_users_role
    CHECK (role IN ('ADMIN', 'OWNER', 'VIEWER'));

-- Each record represents a grant to a named user.
-- Revocation preserves the grant history in this table.
CREATE TABLE document_permissions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL
        REFERENCES documents(id) ON DELETE CASCADE,
    user_id UUID NOT NULL
        REFERENCES app_users(id) ON DELETE CASCADE,
    permission VARCHAR(10) NOT NULL,
    granted_by UUID NOT NULL
        REFERENCES app_users(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID
        REFERENCES app_users(id) ON DELETE SET NULL,

    CONSTRAINT chk_document_permissions_type
        CHECK (permission IN ('READ', 'EDIT')),

    CONSTRAINT chk_document_permissions_revocation
        CHECK (revoked_at IS NOT NULL OR revoked_by IS NULL)
);

-- A user may have only one active grant for a document.
CREATE UNIQUE INDEX uq_document_permissions_active
    ON document_permissions (document_id, user_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_document_permissions_user
    ON document_permissions (user_id, document_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_document_permissions_document
    ON document_permissions (document_id)
    WHERE revoked_at IS NULL;

-- Audit records deliberately do not have foreign keys to users
-- or documents. Deleting an account or document must not erase
-- the historical evidence of an event.
CREATE TABLE audit_events (
    event_no BIGINT PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    actor_user_id UUID,
    actor_email VARCHAR(320) NOT NULL,
    action VARCHAR(50) NOT NULL,
    outcome VARCHAR(12) NOT NULL,
    document_id UUID,
    document_name VARCHAR(255),
    target_user_id UUID,
    target_email VARCHAR(320),
    event_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remote_ip VARCHAR(64),
    user_agent VARCHAR(512),
    reason_code VARCHAR(100),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    previous_hash CHAR(64) NOT NULL,
    event_hash CHAR(64) NOT NULL,

    CONSTRAINT chk_audit_events_outcome
        CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))
);

CREATE INDEX idx_audit_events_time
    ON audit_events (event_time DESC);

CREATE INDEX idx_audit_events_actor_time
    ON audit_events (actor_user_id, event_time DESC);

CREATE INDEX idx_audit_events_document_time
    ON audit_events (document_id, event_time DESC);

CREATE INDEX idx_audit_events_action_time
    ON audit_events (action, event_time DESC);

-- Serialize hash-chain writes using one locked state row.
CREATE TABLE audit_chain_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    last_event_no BIGINT NOT NULL DEFAULT 0,
    last_event_hash CHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO audit_chain_state (
    id,
    last_event_no,
    last_event_hash
)
VALUES (
    1,
    0,
    REPEAT('0', 64)
);

-- Block ordinary UPDATE, DELETE, and TRUNCATE operations on audit
-- records. Production must also separate migration ownership from
-- the runtime database account.
CREATE FUNCTION prevent_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Audit records are append-only';
END;
$$;

CREATE TRIGGER trg_audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_mutation();

CREATE TRIGGER trg_audit_events_no_truncate
    BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT
    EXECUTE FUNCTION prevent_audit_mutation();
