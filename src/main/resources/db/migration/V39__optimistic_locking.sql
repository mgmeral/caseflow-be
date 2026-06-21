-- V39: Add optimistic locking version columns to tickets and assignments

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE assignments
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
