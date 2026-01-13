-- V3__create_password_reset_tokens_table.sql
-- Stores password reset tokens securely (hash only), one-time use, short-lived.

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used_at TIMESTAMP WITHOUT TIME ZONE NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

-- A user can have multiple tokens, but the token hash must be unique
CREATE UNIQUE INDEX ux_password_reset_tokens_token_hash
    ON password_reset_tokens (token_hash);

-- Helpful for cleanup jobs and lookups
CREATE INDEX ix_password_reset_tokens_user_id
    ON password_reset_tokens (user_id);

CREATE INDEX ix_password_reset_tokens_expires_at
    ON password_reset_tokens (expires_at);
