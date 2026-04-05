-- ── P1 Phase 1 migration ─────────────────────────────────────────────────────
-- Covers: tag system, dispatch template metadata, reporting indexes

-- ── 1. Tag controlled vocabulary ─────────────────────────────────────────────

CREATE TABLE tags (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100)  NOT NULL UNIQUE,
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    color       VARCHAR(20),
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by  BIGINT
);

COMMENT ON TABLE  tags IS 'Controlled tag vocabulary — codes are normalized UPPER_SNAKE_CASE.';
COMMENT ON COLUMN tags.code IS 'Unique, stable, normalized tag code (e.g. BUG, FEATURE_REQUEST).';
COMMENT ON COLUMN tags.color IS 'Optional UI hint, e.g. "#FF5733" or CSS color name.';

-- ── 2. Ticket-tag join table ──────────────────────────────────────────────────

CREATE TABLE ticket_tags (
    ticket_id   BIGINT      NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    tag_id      BIGINT      NOT NULL REFERENCES tags(id),
    tagged_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tagged_by   BIGINT,
    PRIMARY KEY (ticket_id, tag_id)
);

COMMENT ON TABLE ticket_tags IS 'Many-to-many link between tickets and tags. PK prevents duplicates.';

-- ── 3. Outbound dispatch: template audit metadata ─────────────────────────────
-- Persists the template that was used and whether the agent edited the rendered content.

ALTER TABLE outbound_email_dispatches
    ADD COLUMN IF NOT EXISTS applied_template_id   BIGINT,
    ADD COLUMN IF NOT EXISTS applied_template_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS content_was_edited    BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN outbound_email_dispatches.applied_template_id IS
    'FK to mail_templates.id — the template applied when this dispatch was rendered.';
COMMENT ON COLUMN outbound_email_dispatches.applied_template_code IS
    'Denormalised template code at send time — stable even if the template is later deleted.';
COMMENT ON COLUMN outbound_email_dispatches.content_was_edited IS
    'True when the agent modified the template-rendered content before sending.';

-- ── 4. Reporting performance indexes ─────────────────────────────────────────

-- Ticket list by customer + date range (customer report)
CREATE INDEX IF NOT EXISTS idx_tickets_customer_created
    ON tickets (customer_id, created_at);

-- Ticket list by status (aggregate bucketing)
CREATE INDEX IF NOT EXISTS idx_tickets_status
    ON tickets (status);

-- Ticket-tag join: look up all tags for a set of tickets
CREATE INDEX IF NOT EXISTS idx_ticket_tags_ticket
    ON ticket_tags (ticket_id);

-- Tag lookup by active flag
CREATE INDEX IF NOT EXISTS idx_tags_active
    ON tags (is_active);
