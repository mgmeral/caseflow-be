-- V5: Add description to groups table
-- Used by admin UIs to explain what a group is for.
ALTER TABLE groups ADD COLUMN IF NOT EXISTS description TEXT;
