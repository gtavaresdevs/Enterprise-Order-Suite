-- D16: a family is every token descended from one login. Reuse of a rotated token revokes
-- the whole family. Tokens issued before this migration have no known lineage, so each
-- becomes a family of one and keeps working.
ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;

UPDATE refresh_tokens SET family_id = gen_random_uuid();

ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
