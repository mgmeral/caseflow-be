-- V13: IMAP polling support, enriched ingress events, remove contact-centric email settings.
--
-- Routing model is now customer-based (not contact-based).
-- Contacts remain in the database for CRM purposes but are no longer part of email routing.

-- ── email_mailboxes: IMAP polling fields ──────────────────────────────────────

ALTER TABLE email_mailboxes
    ADD COLUMN IF NOT EXISTS imap_host            VARCHAR(255),
    ADD COLUMN IF NOT EXISTS imap_port            INTEGER,
    ADD COLUMN IF NOT EXISTS imap_username        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS imap_password        VARCHAR(512),
    ADD COLUMN IF NOT EXISTS imap_use_ssl         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS imap_folder          VARCHAR(255) NOT NULL DEFAULT 'INBOX',
    ADD COLUMN IF NOT EXISTS polling_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS poll_interval_seconds INTEGER     NOT NULL DEFAULT 60,
    ADD COLUMN IF NOT EXISTS last_seen_uid        BIGINT,
    ADD COLUMN IF NOT EXISTS last_poll_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_poll_error      TEXT;

-- ── email_ingress_events: threading headers + body + routing fields ───────────
-- These fields are needed so Stage-2 processing can perform correct threading
-- and reply-target resolution without re-fetching the original message.

ALTER TABLE email_ingress_events
    ADD COLUMN IF NOT EXISTS in_reply_to         VARCHAR(512),
    ADD COLUMN IF NOT EXISTS raw_references      TEXT,
    ADD COLUMN IF NOT EXISTS raw_reply_to        VARCHAR(512),
    ADD COLUMN IF NOT EXISTS raw_cc              TEXT,
    ADD COLUMN IF NOT EXISTS text_body           TEXT,
    ADD COLUMN IF NOT EXISTS html_body           TEXT,
    ADD COLUMN IF NOT EXISTS envelope_recipient  VARCHAR(512);

-- Index to speed up thread resolution via In-Reply-To header
CREATE INDEX IF NOT EXISTS idx_ingress_events_in_reply_to ON email_ingress_events (in_reply_to);

-- ── customer_email_settings: remove contact-centric columns ──────────────────
-- Routing is now purely customer-rule-based.
-- matchingStrategy, trustedContactsOnly, autoCreateContact had no routing effect
-- in the V5+ pipeline and are removed to eliminate contact assumptions.

ALTER TABLE customer_email_settings
    DROP COLUMN IF EXISTS matching_strategy,
    DROP COLUMN IF EXISTS trusted_contacts_only,
    DROP COLUMN IF EXISTS auto_create_contact;
