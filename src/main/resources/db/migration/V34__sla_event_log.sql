-- V34: SLA event log for idempotent breach/warning/recovery lifecycle tracking.
--
-- One row per ticket, representing the *current* SLA lifecycle state last emitted
-- by SlaBreachCheckerJob. The job compares computed state against this log to decide
-- whether a new event/notification should be emitted (state-change guard).
--
-- Lifecycle:
--   (no entry) → WARNING  → BREACHED  → (deleted on terminal/recovery)
--                             ↑                    ↓
--                           (state can regress to WARNING only on policy change)
--                (new entry when ticket re-opened and SLA clock restarts)

CREATE TABLE sla_event_log (
    id           BIGSERIAL    PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL,
    sla_state    VARCHAR(50)  NOT NULL,  -- WARNING | BREACHED | RECOVERED
    recorded_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_sla_event_log_ticket UNIQUE (ticket_id)
);

CREATE INDEX idx_sla_event_log_ticket ON sla_event_log (ticket_id);
