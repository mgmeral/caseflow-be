-- Replace enum-based group type column with a reference table

CREATE TABLE IF NOT EXISTS group_types (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE groups DROP COLUMN IF EXISTS type;
ALTER TABLE groups ADD COLUMN group_type_id BIGINT REFERENCES group_types(id);
