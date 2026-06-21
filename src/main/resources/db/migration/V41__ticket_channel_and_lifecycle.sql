-- Ticket channel (how the ticket arrived) and lifecycle tracking
ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    ADD COLUMN IF NOT EXISTS resolved_by BIGINT,
    ADD COLUMN IF NOT EXISTS closed_by BIGINT;
