-- 1. Ensure all standard roles exist safely
INSERT INTO roles (name, created_at, updated_at)
VALUES
    ('SUPER_ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- 2. Seed the Root Super Admin
INSERT INTO users (first_name, last_name, email, password, active, role_id, created_at, updated_at)
SELECT
    'Gabriel',
    'Tavares Almeida',
    'gtavaresdev@gmail.com',
    '$2a$10$Ewi2tfvf6B2n35PKMjOWWedG5WYgKglip8Bia0FpZru4yHqU.cvbO', -- PASTE YOUR HASH HERE! (Keep the single quotes)
    true,
    r.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM roles r
WHERE r.name = 'SUPER_ADMIN'
ON CONFLICT (email) DO NOTHING;
