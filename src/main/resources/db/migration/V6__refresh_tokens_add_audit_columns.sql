ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE;

UPDATE refresh_tokens
SET created_at = COALESCE(created_at, NOW()),
    updated_at = COALESCE(updated_at, NOW());

ALTER TABLE refresh_tokens
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
