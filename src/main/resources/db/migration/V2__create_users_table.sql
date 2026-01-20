-- Creates users table
-- Required by BaseEntity (created_at, updated_at)
-- Must exist before password_reset_tokens due to FK dependency

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,

    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),

    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,

    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    role_id BIGINT NOT NULL,

    CONSTRAINT uk_users_email UNIQUE (email),

    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id)
        REFERENCES roles (id)
);
