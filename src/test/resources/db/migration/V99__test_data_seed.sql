-- Test Data Seed for Integration Tests
-- This migration only runs in test environments

-- Create test user: user@test.com with password Password123!
INSERT INTO users (first_name, last_name, email, password, active, role_id, created_at, updated_at)
SELECT
    'Test',
    'User',
    'user@test.com',
    '$2a$10$X5wFWHkJ5HZGmDI/mJLExOmPjKZAEJGlKfLLT5nFh3.vqHVqRXKWy', -- Password123!
    true,
    r.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM roles r
WHERE r.name = 'USER'
ON CONFLICT (email) DO NOTHING;

-- Create test user: gtavaresdev+enterpriseordersuitetestrefreshtokentest@gmail.com with password Test123!
INSERT INTO users (first_name, last_name, email, password, active, role_id, created_at, updated_at)
SELECT
    'Refresh',
    'Test',
    'gtavaresdev+enterpriseordersuitetestrefreshtokentest@gmail.com',
    '$2a$10$TQzCXKJ0IgKt8VzZxBJhWO5GQcX5KQgJ8Oq9MxHvYxYx5yZXvLYHa', -- Test123!
    true,
    r.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM roles r
WHERE r.name = 'USER'
ON CONFLICT (email) DO NOTHING;

-- Create user profiles for test users
INSERT INTO user_profiles (user_id, created_at, updated_at)
SELECT u.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u
WHERE u.email IN ('user@test.com', 'gtavaresdev+enterpriseordersuitetestrefreshtokentest@gmail.com')
ON CONFLICT (user_id) DO NOTHING;
