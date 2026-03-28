-- Dynamic role reference model
-- Permissions are defined in code (Permission enum); codes are stored as strings here.

CREATE TABLE roles (
    id           BIGSERIAL    PRIMARY KEY,
    code         VARCHAR(100) NOT NULL UNIQUE,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    ticket_scope VARCHAR(50)  NOT NULL DEFAULT 'ALL',
    version      INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE role_permissions (
    role_id         BIGINT       NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_code)
);
