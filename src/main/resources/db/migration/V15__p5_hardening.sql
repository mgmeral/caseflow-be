-- V15: P5 hardening — ticket publicId, dispatch context fields, attachment storage keys,
-- routing rule subdomains, mailbox-level unknown sender policy.

-- ── A. Ticket public UUID ─────────────────────────────────────────────────────
-- Adds a stable external-facing UUID to every ticket (numeric id remains internal PK).
-- Used as the stable prefix in attachment object keys.

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS public_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX IF NOT EXISTS idx_tickets_public_id ON tickets(public_id);

COMMENT ON COLUMN tickets.public_id IS
    'Stable external-facing UUID. Numeric id is internal PK. '
    'ticketNo remains human-readable business identifier. '
    'public_id is used as the attachment storage path prefix.';

-- ── B. OutboundEmailDispatch: mailbox context, source event, sentBy, failureCategory ───

ALTER TABLE outbound_email_dispatches
    ADD COLUMN IF NOT EXISTS mailbox_id               BIGINT,
    ADD COLUMN IF NOT EXISTS source_ingress_event_id  BIGINT,
    ADD COLUMN IF NOT EXISTS sent_by_user_id          BIGINT,
    ADD COLUMN IF NOT EXISTS resolved_to_address      VARCHAR(512),
    ADD COLUMN IF NOT EXISTS failure_category         VARCHAR(100);

COMMENT ON COLUMN outbound_email_dispatches.mailbox_id IS
    'The mailbox used to send this reply. References email_mailboxes.id. '
    'Used by OutboundDispatchScheduler to load mailbox-specific SMTP settings.';

COMMENT ON COLUMN outbound_email_dispatches.source_ingress_event_id IS
    'The inbound EmailIngressEvent this reply is in response to. '
    'Used for reply-target derivation and timeline correlation.';

COMMENT ON COLUMN outbound_email_dispatches.sent_by_user_id IS
    'The agent user who initiated this outbound reply.';

COMMENT ON COLUMN outbound_email_dispatches.resolved_to_address IS
    'The actual reply-to address resolved by the backend (from replyTo/from headers). '
    'Stored separately from to_address for audit purposes.';

COMMENT ON COLUMN outbound_email_dispatches.failure_category IS
    'Categorised failure reason: SMTP_AUTH_FAILURE, TLS_FAILURE, INVALID_ADDRESS, '
    'MAILBOX_INACTIVE, UNCONFIGURED, UNKNOWN.';

-- ── C. AttachmentMetadata: ingressEventId, ticketPublicId, storageStage ──────

ALTER TABLE attachment_metadata
    ADD COLUMN IF NOT EXISTS ingress_event_id  BIGINT,
    ADD COLUMN IF NOT EXISTS ticket_public_id  UUID,
    ADD COLUMN IF NOT EXISTS storage_stage     VARCHAR(50) NOT NULL DEFAULT 'FINAL';

COMMENT ON COLUMN attachment_metadata.ingress_event_id IS
    'References email_ingress_events.id for attachments ingested via IMAP.';

COMMENT ON COLUMN attachment_metadata.ticket_public_id IS
    'Denormalised copy of tickets.public_id for use in stable object key paths.';

COMMENT ON COLUMN attachment_metadata.storage_stage IS
    'STAGING: stored under mailbox-scoped staging prefix before ticket is assigned. '
    'FINAL: stored under stable ticket/email prefix after routing completes.';

-- Index for attachment lookups by ingress event
CREATE INDEX IF NOT EXISTS idx_attachment_metadata_ingress_event
    ON attachment_metadata(ingress_event_id)
    WHERE ingress_event_id IS NOT NULL;

-- ── D. EmailMailbox: mailbox-level unknown sender policy ──────────────────────
-- When unknown sender arrives, check mailbox policy before falling back to global.

ALTER TABLE email_mailboxes
    ADD COLUMN IF NOT EXISTS unknown_sender_policy VARCHAR(50);

COMMENT ON COLUMN email_mailboxes.unknown_sender_policy IS
    'Mailbox-level fallback for unroutable inbound emails. '
    'Overrides global CustomerEmailSettings when set. '
    'Values match UnknownSenderPolicy enum: MANUAL_REVIEW, CREATE_UNMATCHED_TICKET, IGNORE, REJECT.';

-- ── E. CustomerEmailRoutingRule: allowSubdomains per rule ─────────────────────
-- allowSubdomains on CustomerEmailSettings is ignored for routing; per-rule is the right place.

ALTER TABLE customer_email_routing_rules
    ADD COLUMN IF NOT EXISTS allow_subdomains BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN customer_email_routing_rules.allow_subdomains IS
    'When TRUE a DOMAIN rule also matches sub-domains of matchValue '
    '(e.g. rule bigcorp.com also matches mail.bigcorp.com). '
    'Exact-email rules ignore this field.';
