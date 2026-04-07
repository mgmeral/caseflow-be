-- Phase 2-C/D: Scheduled email columns + Phase 2 permission codes

-- ── Scheduled email additions to outbound_email_dispatches ───────────────────

-- Marks a dispatch as an intentionally scheduled (future-dated) send.
-- Regular replies have this false; scheduled emails have this true.
ALTER TABLE outbound_email_dispatches
    ADD COLUMN is_scheduled_send BOOLEAN NOT NULL DEFAULT FALSE;

-- Tracks when a scheduled dispatch was explicitly canceled.
ALTER TABLE outbound_email_dispatches
    ADD COLUMN canceled_at TIMESTAMPTZ;

-- Add CANCELED as an allowed status value (enforced at application layer via enum).
COMMENT ON COLUMN outbound_email_dispatches.status IS
    'PENDING — queued. '
    'SENDING — in progress. '
    'SENT — delivered to SMTP. '
    'FAILED — last attempt failed, retry eligible. '
    'PERMANENTLY_FAILED — max attempts exhausted. '
    'CANCELED — user-canceled (scheduled emails only).';

-- Index for the scheduled email list UI (my pending scheduled sends for a ticket)
CREATE INDEX idx_outbound_scheduled_pending
    ON outbound_email_dispatches (ticket_id, scheduled_at)
    WHERE is_scheduled_send = TRUE AND status = 'PENDING';

COMMENT ON COLUMN outbound_email_dispatches.is_scheduled_send IS
    'TRUE when this dispatch was created as a scheduled future send, '
    'as opposed to a reply queued for immediate dispatch.';

-- ── Phase 2 new permissions ──────────────────────────────────────────────────
-- Permission codes are stored as strings in role_permissions; document them here.

COMMENT ON TABLE integration_jobs IS
    'Durable, retryable external integration actions. '
    'Workers use SKIP LOCKED to claim rows without contention. '
    'idempotency_key prevents accidental duplicate actions. '
    'New Phase-2 permissions: '
    '  INTEGRATION_CONFIG_MANAGE — manage Jira/Slack/Teams configs. '
    '  INTEGRATION_JOB_VIEW      — view integration job status. '
    '  SCHEDULED_EMAIL_MANAGE    — create/cancel scheduled outbound emails.';
