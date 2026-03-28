-- V4: Entity-level constraint hardening
-- Enforces invariants that were previously only checked at application level.

-- ── contacts: globally unique email ──────────────────────────────────────────
-- findByEmail() returns Optional (assumes single result); the email ingest flow
-- uses it to map a sender address to a customer. Duplicates would cause
-- IncorrectResultSizeDataAccessException or wrong-customer resolution.
--
-- The existing non-unique idx_contacts_email is replaced by this unique constraint,
-- which implicitly creates its own index.
DROP INDEX IF EXISTS idx_contacts_email;
ALTER TABLE contacts ADD CONSTRAINT uq_contacts_email UNIQUE (email);

-- ── contacts: at most one primary contact per customer ────────────────────────
-- ContactService.clearPrimary() enforces this at app level, but a concurrent
-- create/update could slip through. Partial unique index is the correct DB primitive.
CREATE UNIQUE INDEX IF NOT EXISTS uidx_contacts_primary_per_customer
    ON contacts (customer_id) WHERE is_primary = TRUE;

-- ── attachment_metadata: must belong to at least a ticket or an email ─────────
-- Both ticket_id and email_id are nullable, but an attachment with neither is
-- orphaned and unreachable. Every current code path sets at least one.
ALTER TABLE attachment_metadata
    ADD CONSTRAINT chk_attachment_has_owner
    CHECK (ticket_id IS NOT NULL OR email_id IS NOT NULL);

-- ── assignments: must target at least a user or a group ───────────────────────
-- The AssignTicketRequest DTO does not require either field, so both can arrive
-- null. An assignment with no user and no group is semantically empty.
ALTER TABLE assignments
    ADD CONSTRAINT chk_assignment_has_target
    CHECK (assigned_user_id IS NOT NULL OR assigned_group_id IS NOT NULL);
