-- V14: Mailbox hardening — safe onboarding, multi-instance lease, attachment pipeline.
--
-- A) initial_sync_strategy: controls first-poll behaviour (default: START_FROM_LATEST, production-safe).
-- B) poll_locked_by / poll_leased_until: DB-level lease for multi-instance polling coordination.
-- C) attachments_json: stores IMAP-extracted attachment metadata for Stage-2 processing.

-- ── A. email_mailboxes: initial sync strategy ────────────────────────────────

ALTER TABLE email_mailboxes
    ADD COLUMN IF NOT EXISTS initial_sync_strategy VARCHAR(50) NOT NULL DEFAULT 'START_FROM_LATEST';

COMMENT ON COLUMN email_mailboxes.initial_sync_strategy IS
    'Controls first-poll behaviour when last_seen_uid is null. '
    'START_FROM_LATEST (default): advance cursor to current UID without ingesting history. '
    'BACKFILL_ALL: ingest from UID 1 (operator must set this intentionally).';

-- ── B. email_mailboxes: poll lease for multi-instance safety ─────────────────

ALTER TABLE email_mailboxes
    ADD COLUMN IF NOT EXISTS poll_locked_by   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS poll_leased_until TIMESTAMPTZ;

COMMENT ON COLUMN email_mailboxes.poll_locked_by IS
    'Instance ID of the application node currently holding the poll lease. '
    'NULL when the mailbox is not being polled.';

COMMENT ON COLUMN email_mailboxes.poll_leased_until IS
    'When the current poll lease expires. Another node may reclaim after this timestamp '
    '(crash-recovery safety).';

-- Index for fast lease-expiry queries (used in tryClaimMailbox UPDATE)
CREATE INDEX IF NOT EXISTS idx_mailboxes_poll_lease
    ON email_mailboxes (polling_enabled, is_active, poll_leased_until)
    WHERE polling_enabled = true AND is_active = true;

-- ── C. email_ingress_events: attachment metadata from IMAP ───────────────────

ALTER TABLE email_ingress_events
    ADD COLUMN IF NOT EXISTS attachments_json TEXT;

COMMENT ON COLUMN email_ingress_events.attachments_json IS
    'JSON array of IngressAttachmentData records for attachments extracted from IMAP messages. '
    'NULL for webhook-ingest events. Populated by ImapMailboxPoller (Stage 1). '
    'Consumed by EmailIngressServiceImpl (Stage 2) to persist AttachmentMetadata records.';
