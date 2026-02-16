CREATE TABLE identity_audit_events (
    id BIGSERIAL PRIMARY KEY,

    type VARCHAR(64) NOT NULL,
    actor_user_id BIGINT,
    target_user_id BIGINT NOT NULL,
    metadata TEXT,

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_identity_audit_events_created_at
    ON identity_audit_events (created_at DESC);

CREATE INDEX idx_identity_audit_events_target_user
    ON identity_audit_events (target_user_id);

CREATE INDEX idx_identity_audit_events_actor_user
    ON identity_audit_events (actor_user_id);

CREATE INDEX idx_identity_audit_events_type
    ON identity_audit_events (type);
