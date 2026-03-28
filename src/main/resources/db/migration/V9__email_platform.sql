-- V9: Email platform — mailboxes, ingress events, outbound dispatches,
--     customer email settings, and routing rules.

-- ── email_mailboxes ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS email_mailboxes (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    address         VARCHAR(255) NOT NULL UNIQUE,
    provider_type   VARCHAR(50)  NOT NULL DEFAULT 'SMTP_RELAY',
    inbound_mode    VARCHAR(50)  NOT NULL DEFAULT 'WEBHOOK',
    outbound_mode   VARCHAR(50)  NOT NULL DEFAULT 'SMTP',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    smtp_host       VARCHAR(255),
    smtp_port       INTEGER,
    smtp_username   VARCHAR(255),
    smtp_password   VARCHAR(512),
    smtp_use_ssl    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── email_ingress_events ───────────────────────────────────────────────────────
-- One row per inbound email attempt. Stage-1 stores the raw event; Stage-2 processes it.
CREATE TABLE IF NOT EXISTS email_ingress_events (
    id                  BIGSERIAL    PRIMARY KEY,
    mailbox_id          BIGINT       REFERENCES email_mailboxes(id),
    message_id          VARCHAR(512) NOT NULL,
    raw_from            VARCHAR(512) NOT NULL,
    raw_to              TEXT,
    raw_subject         TEXT,
    received_at         TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(50)  NOT NULL DEFAULT 'RECEIVED',
    failure_reason      TEXT,
    processing_attempts INTEGER      NOT NULL DEFAULT 0,
    last_attempt_at     TIMESTAMPTZ,
    processed_at        TIMESTAMPTZ,
    document_id         VARCHAR(255),
    ticket_id           BIGINT       REFERENCES tickets(id)
);

CREATE INDEX IF NOT EXISTS idx_ingress_events_message_id ON email_ingress_events (message_id);
CREATE INDEX IF NOT EXISTS idx_ingress_events_status     ON email_ingress_events (status);
CREATE INDEX IF NOT EXISTS idx_ingress_events_mailbox_id ON email_ingress_events (mailbox_id);
CREATE INDEX IF NOT EXISTS idx_ingress_events_ticket_id  ON email_ingress_events (ticket_id);

-- ── outbound_email_dispatches ──────────────────────────────────────────────────
-- Durable outbound send queue — one row per email to deliver.
CREATE TABLE IF NOT EXISTS outbound_email_dispatches (
    id                   BIGSERIAL    PRIMARY KEY,
    ticket_id            BIGINT       REFERENCES tickets(id),
    message_id           VARCHAR(512) NOT NULL UNIQUE,
    from_address         VARCHAR(512) NOT NULL,
    to_address           VARCHAR(512) NOT NULL,
    subject              TEXT         NOT NULL,
    text_body            TEXT,
    html_body            TEXT,
    in_reply_to_message_id VARCHAR(512),
    status               VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    attempts             INTEGER      NOT NULL DEFAULT 0,
    last_attempt_at      TIMESTAMPTZ,
    sent_at              TIMESTAMPTZ,
    failure_reason       TEXT,
    scheduled_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dispatch_status    ON outbound_email_dispatches (status);
CREATE INDEX IF NOT EXISTS idx_dispatch_ticket_id ON outbound_email_dispatches (ticket_id);

-- ── customer_email_settings ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS customer_email_settings (
    id                    BIGSERIAL   PRIMARY KEY,
    customer_id           BIGINT      NOT NULL UNIQUE REFERENCES customers(id),
    unknown_sender_policy VARCHAR(50) NOT NULL DEFAULT 'MANUAL_REVIEW',
    matching_strategy     VARCHAR(50) NOT NULL DEFAULT 'CONTACT_FIRST',
    is_active             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── customer_email_routing_rules ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS customer_email_routing_rules (
    id                BIGSERIAL    PRIMARY KEY,
    customer_id       BIGINT       NOT NULL REFERENCES customers(id),
    sender_match_type VARCHAR(50)  NOT NULL,
    match_value       VARCHAR(512) NOT NULL,
    priority          INTEGER      NOT NULL DEFAULT 100,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_routing_rules_customer_id  ON customer_email_routing_rules (customer_id);
CREATE UNIQUE INDEX IF NOT EXISTS uidx_routing_rules_match
    ON customer_email_routing_rules (customer_id, sender_match_type, lower(match_value));

-- ── attachment_metadata additions ──────────────────────────────────────────────
ALTER TABLE attachment_metadata
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(50) NOT NULL DEFAULT 'UPLOAD';
