-- V38: Missing FK constraints on assignments/transfers + critical performance indexes

-- ── FK constraints ────────────────────────────────────────────────────────────

ALTER TABLE assignments
    ADD CONSTRAINT fk_assignments_user
        FOREIGN KEY (assigned_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE assignments
    ADD CONSTRAINT fk_assignments_group
        FOREIGN KEY (assigned_group_id) REFERENCES groups(id) ON DELETE SET NULL;

ALTER TABLE transfers
    ADD CONSTRAINT fk_transfers_from_group
        FOREIGN KEY (from_group_id) REFERENCES groups(id) ON DELETE SET NULL;

ALTER TABLE transfers
    ADD CONSTRAINT fk_transfers_to_group
        FOREIGN KEY (to_group_id) REFERENCES groups(id) ON DELETE SET NULL;

-- contacts → customers: enforce at DB level (JPA cascade already handles inserts; this covers direct SQL)
ALTER TABLE contacts
    ADD CONSTRAINT fk_contacts_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE;

-- ── Performance indexes ───────────────────────────────────────────────────────

-- Primary ticket listing filter: customer + status
CREATE INDEX IF NOT EXISTS idx_tickets_customer_status
    ON tickets(customer_id, status);

-- Filtered listings and SLA reporting
CREATE INDEX IF NOT EXISTS idx_tickets_priority
    ON tickets(priority);

CREATE INDEX IF NOT EXISTS idx_tickets_created_at
    ON tickets(created_at);

-- Assignment lookups (agent workload, active assignment queries)
CREATE INDEX IF NOT EXISTS idx_assignments_assigned_user
    ON assignments(assigned_user_id);

CREATE INDEX IF NOT EXISTS idx_assignments_ticket_status
    ON assignments(ticket_id, status);
