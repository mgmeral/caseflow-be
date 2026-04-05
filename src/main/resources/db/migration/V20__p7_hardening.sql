-- P7 hardening: SMTP transport mode, structured ticket history, attachment traceability

-- ── D) SMTP transport mode ────────────────────────────────────────────────────
-- Add explicit STARTTLS flag alongside existing smtpUseSsl.
-- Port 465 => smtp_use_ssl=true,  smtp_starttls=false (implicit SSL)
-- Port 587 => smtp_use_ssl=false, smtp_starttls=true  (STARTTLS)
ALTER TABLE email_mailboxes
    ADD COLUMN smtp_starttls BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN email_mailboxes.smtp_starttls IS
    'When true, use STARTTLS (explicit TLS) for outbound SMTP. '
    'Mutually exclusive with smtp_use_ssl. '
    'Port 587 with STARTTLS is the correct setting for Gmail/Google Workspace.';

COMMENT ON COLUMN email_mailboxes.smtp_use_ssl IS
    'When true, use implicit SSL/TLS for outbound SMTP (port 465). '
    'Mutually exclusive with smtp_starttls. '
    'Do not set both true simultaneously.';

-- ── C) Structured ticket history / event stream ───────────────────────────────
-- Make performed_by nullable so system-generated events (no actor) can be recorded.
ALTER TABLE ticket_history
    ALTER COLUMN performed_by DROP NOT NULL;

-- Stable public UUID of the ticket — denormalised for stable API paths without
-- requiring a join to tickets.
ALTER TABLE ticket_history
    ADD COLUMN ticket_public_id UUID;

-- Indicates whether the event was triggered by a human user or the system.
-- 'USER' events always have a performed_by; 'SYSTEM' events may not.
ALTER TABLE ticket_history
    ADD COLUMN source_type VARCHAR(50) NOT NULL DEFAULT 'USER';

-- Short human-readable event summary for display in the operation log UI.
ALTER TABLE ticket_history
    ADD COLUMN summary TEXT;

-- Structured snapshot of the old / new field value (JSON) for diff display.
ALTER TABLE ticket_history
    ADD COLUMN old_value_json TEXT;

ALTER TABLE ticket_history
    ADD COLUMN new_value_json TEXT;

-- Arbitrary event-specific metadata (JSON): dispatchId, templateCode, etc.
ALTER TABLE ticket_history
    ADD COLUMN metadata_json TEXT;

-- Backfill ticket_public_id for existing rows so the column is useful immediately.
UPDATE ticket_history h
   SET ticket_public_id = t.public_id
  FROM tickets t
 WHERE t.id = h.ticket_id
   AND h.ticket_public_id IS NULL;

COMMENT ON COLUMN ticket_history.source_type IS
    'USER — event triggered by an authenticated agent. '
    'SYSTEM — event triggered by a background process (scheduler, routing engine, etc.).';

COMMENT ON COLUMN ticket_history.summary IS
    'Short description shown in the UI operation log (e.g. "Status changed: NEW → IN_PROGRESS").';

COMMENT ON COLUMN ticket_history.metadata_json IS
    'Event-type-specific metadata as a JSON object, e.g. '
    '{"dispatchId": 42, "toAddress": "customer@example.com"} for OUTBOUND_REPLY_QUEUED.';
