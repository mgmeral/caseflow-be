-- Ticket merge/split: parent_ticket_id links a merged ticket to its merge target.
-- null = standalone ticket; non-null = this ticket was merged INTO parent_ticket_id.
ALTER TABLE tickets
    ADD COLUMN parent_ticket_id BIGINT REFERENCES tickets(id) ON DELETE SET NULL,
    ADD COLUMN merged_at        TIMESTAMP,
    ADD COLUMN merged_by        BIGINT;

CREATE INDEX idx_tickets_parent_ticket_id ON tickets (parent_ticket_id)
    WHERE parent_ticket_id IS NOT NULL;
