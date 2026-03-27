-- CaseFlow initial schema
-- V1: baseline schema aligned with JPA entities

-- ── customers ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS customers (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    code       VARCHAR(100),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── contacts ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS contacts (
    id          BIGSERIAL    PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customers(id),
    email       VARCHAR(255) NOT NULL,
    name        VARCHAR(255),
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_contacts_email       ON contacts (email);
CREATE INDEX IF NOT EXISTS idx_contacts_customer_id ON contacts (customer_id);

-- ── groups ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS groups (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    type       VARCHAR(50)  NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── users ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(255) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ
);

-- ── user_groups (join table) ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_groups (
    user_id  BIGINT NOT NULL REFERENCES users(id),
    group_id BIGINT NOT NULL REFERENCES groups(id),
    PRIMARY KEY (user_id, group_id)
);

-- ── tickets ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tickets (
    id                BIGSERIAL    PRIMARY KEY,
    ticket_no         VARCHAR(255) NOT NULL UNIQUE,
    subject           VARCHAR(255) NOT NULL,
    description       TEXT,
    status            VARCHAR(50)  NOT NULL DEFAULT 'NEW',
    priority          VARCHAR(50)  NOT NULL DEFAULT 'MEDIUM',
    customer_id       BIGINT       REFERENCES customers(id),
    assigned_user_id  BIGINT       REFERENCES users(id),
    assigned_group_id BIGINT       REFERENCES groups(id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    closed_at         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tickets_status            ON tickets (status);
CREATE INDEX IF NOT EXISTS idx_tickets_customer_id       ON tickets (customer_id);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned_user_id  ON tickets (assigned_user_id);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned_group_id ON tickets (assigned_group_id);

-- ── ticket_history ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ticket_history (
    id           BIGSERIAL    PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL REFERENCES tickets(id),
    action_type  VARCHAR(100) NOT NULL,
    -- performed_by is nullable: system actions (e.g., email-created tickets) have no user
    performed_by BIGINT,
    performed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    details      TEXT
);

CREATE INDEX IF NOT EXISTS idx_ticket_history_ticket_id ON ticket_history (ticket_id);

-- ── attachment_metadata ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS attachment_metadata (
    id           BIGSERIAL    PRIMARY KEY,
    ticket_id    BIGINT       REFERENCES tickets(id),
    email_id     VARCHAR(255),
    file_name    VARCHAR(255) NOT NULL,
    object_key   VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(255) NOT NULL,
    size         BIGINT       NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_attachment_ticket_id ON attachment_metadata (ticket_id);

-- ── notes ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notes (
    id         BIGSERIAL   PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets(id),
    content    TEXT        NOT NULL,
    created_by BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    type       VARCHAR(50) NOT NULL DEFAULT 'INFO'
);

CREATE INDEX IF NOT EXISTS idx_notes_ticket_id ON notes (ticket_id);

-- ── assignments ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS assignments (
    id                BIGSERIAL   PRIMARY KEY,
    ticket_id         BIGINT      NOT NULL REFERENCES tickets(id),
    assigned_user_id  BIGINT,
    assigned_group_id BIGINT,
    assigned_by       BIGINT      NOT NULL,
    assigned_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    unassigned_at     TIMESTAMPTZ
);

-- Partial unique index: only one active (unassigned_at IS NULL) assignment per ticket
CREATE UNIQUE INDEX IF NOT EXISTS uidx_assignments_active_ticket
    ON assignments (ticket_id) WHERE unassigned_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_assignments_ticket_id ON assignments (ticket_id);

-- ── transfers ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transfers (
    id              BIGSERIAL    PRIMARY KEY,
    ticket_id       BIGINT       NOT NULL REFERENCES tickets(id),
    from_group_id   BIGINT       NOT NULL,
    to_group_id     BIGINT       NOT NULL,
    transferred_by  BIGINT       NOT NULL,
    transferred_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reason          TEXT
);

CREATE INDEX IF NOT EXISTS idx_transfers_ticket_id ON transfers (ticket_id);
