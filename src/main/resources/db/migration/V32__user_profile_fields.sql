-- Extends the users table with self-service profile fields.
-- All columns are nullable — existing rows are unaffected.

ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name  VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name    VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name     VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS locale        VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url    VARCHAR(2048);
