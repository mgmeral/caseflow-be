-- Phase 2-A: Durable integration job foundation
-- Single table for all external integration actions: Jira, Slack, Teams, scheduled email.
-- Workers claim rows with SKIP LOCKED to prevent double-processing across app instances.

CREATE TABLE integration_jobs (
    id                  BIGSERIAL PRIMARY KEY,

    -- What kind of external action this represents
    integration_type    VARCHAR(100) NOT NULL,

    -- Job lifecycle state
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING',

    -- Ticket context (nullable for non-ticket-scoped jobs)
    ticket_id           BIGINT,
    ticket_public_id    UUID,
    customer_id         BIGINT,
    mailbox_id          BIGINT,

    -- Serialized job-specific payload (JSON)
    payload_json        TEXT,

    -- Reference to the external system result (e.g. Jira issue key)
    external_reference  VARCHAR(500),

    -- Prevent accidental duplicate jobs for the same logical action
    idempotency_key     VARCHAR(500) NOT NULL,

    -- Retry tracking
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    max_attempts        INTEGER NOT NULL DEFAULT 3,
    next_attempt_at     TIMESTAMPTZ,
    last_error          TEXT,

    -- Who / what triggered this job
    triggered_by_type   VARCHAR(50) NOT NULL DEFAULT 'USER',  -- USER | SYSTEM | SCHEDULE | EVENT
    created_by          BIGINT,                                -- nullable for SYSTEM/EVENT triggers

    -- Timestamps
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,
    canceled_at         TIMESTAMPTZ,

    CONSTRAINT integration_jobs_idempotency_unique UNIQUE (idempotency_key)
);

-- Fast poll for due PENDING jobs
CREATE INDEX idx_integration_jobs_pending
    ON integration_jobs (status, next_attempt_at)
    WHERE status = 'PENDING';

-- Fast look-up of jobs by ticket (for UI / audit)
CREATE INDEX idx_integration_jobs_ticket
    ON integration_jobs (ticket_id)
    WHERE ticket_id IS NOT NULL;

-- Fast look-up by type + ticket for duplicate guards
CREATE INDEX idx_integration_jobs_type_ticket
    ON integration_jobs (integration_type, ticket_id)
    WHERE ticket_id IS NOT NULL;

COMMENT ON TABLE integration_jobs IS
    'Durable, retryable external integration actions. '
    'Workers use SKIP LOCKED to claim rows without contention. '
    'idempotency_key prevents accidental duplicate actions.';

COMMENT ON COLUMN integration_jobs.status IS
    'PENDING — queued, not yet started. '
    'PROCESSING — a worker holds this row. '
    'SUCCEEDED — external action confirmed. '
    'FAILED — last attempt failed, within retry budget. '
    'PERMANENTLY_FAILED — max attempts exhausted. '
    'CANCELED — user or system explicitly canceled.';

COMMENT ON COLUMN integration_jobs.triggered_by_type IS
    'USER — triggered by an authenticated agent action. '
    'SYSTEM — triggered by a background scheduler. '
    'EVENT — triggered by a domain event (ticket created, etc.).';
