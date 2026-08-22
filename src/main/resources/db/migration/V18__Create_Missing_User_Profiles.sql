INSERT INTO user_profiles (
    user_id,
    phone,
    country,
    timezone,
    department,
    office,
    bio,
    created_at,
    updated_at
)
SELECT
    u.id,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM users u
WHERE NOT EXISTS (
    SELECT 1
    FROM user_profiles up
    WHERE up.user_id = u.id
);
