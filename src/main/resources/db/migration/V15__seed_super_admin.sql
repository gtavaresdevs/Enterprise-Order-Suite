-- Ensure all standard roles exist safely.
--
-- This migration used to also INSERT a root super admin with a real address and bcrypt hash,
-- which committed a credential into every environment the migration ran in. That seed now
-- happens at boot from SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD_HASH - see
-- identity.application.RootSuperAdminSeeder. The roles below stay here: they are schema that
-- six integration test classes resolve by name, not a secret.
INSERT INTO roles (name, created_at, updated_at)
VALUES
    ('SUPER_ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;
