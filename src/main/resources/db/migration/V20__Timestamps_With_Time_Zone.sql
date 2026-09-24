-- D15 (docs/superpowers/specs/2026-09-24-restaurant-ops-phase-0-foundation-design.md):
-- every persisted timestamp is an instant. Converts every remaining
-- TIMESTAMP WITHOUT TIME ZONE column to TIMESTAMP WITH TIME ZONE.
--
-- A naive value carries no zone, so this migration has to say which zone each column was
-- written in. Existing rows came from two writers that did not agree:
--
--   America/Sao_Paulo - columns filled by Hibernate's @CreationTimestamp/@UpdateTimestamp
--     (BaseEntity) or by a column default (NOW(), CURRENT_TIMESTAMP). Both follow the JVM's
--     default zone: Hibernate directly, the defaults through the session TimeZone the JDBC
--     driver sets from it. Every environment that ran the pre-V20 code did so in Brasilia;
--     for the local environment the JVM default was checked before writing this file.
--
--   UTC - columns the auth services filled from LocalDateTime.now(clock), where the Clock
--     bean is Clock.systemUTC().
--
-- Getting a column's zone wrong does not fail. It silently shifts every existing row by
-- three hours - for expires_at, that moves every live token's expiry.
--
-- order_items and order_history were created WITH TIME ZONE and need nothing.

ALTER TABLE roles
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE identity_audit_events
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE orders
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE products
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE user_profiles
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE refresh_tokens
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN used_at    TYPE TIMESTAMP WITH TIME ZONE USING used_at    AT TIME ZONE 'UTC',
    ALTER COLUMN revoked_at TYPE TIMESTAMP WITH TIME ZONE USING revoked_at AT TIME ZONE 'UTC';

ALTER TABLE password_reset_tokens
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN used_at    TYPE TIMESTAMP WITH TIME ZONE USING used_at    AT TIME ZONE 'UTC';

ALTER TABLE password_history
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC';
