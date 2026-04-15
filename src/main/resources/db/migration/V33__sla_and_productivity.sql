-- V33: SLA backbone, template productivity metadata, bulk/assist scaffolding,
--      automation rule engine foundation.

-- ── SLA Policy Configurations ────────────────────────────────────────────────
-- Supports: global default + priority-override.
-- Group/customer scopes are modeled but not activated in this milestone.
CREATE TABLE sla_policy_configs (
    id                              BIGSERIAL PRIMARY KEY,
    name                            VARCHAR(255) NOT NULL,
    scope                           VARCHAR(50)  NOT NULL DEFAULT 'GLOBAL',
    -- Priority scope: LOW, MEDIUM, HIGH, CRITICAL — null for GLOBAL/GROUP scope
    priority                        VARCHAR(50),
    -- Group scope: FK not enforced here to keep migration portable
    group_id                        BIGINT,
    first_response_target_minutes   INTEGER NOT NULL DEFAULT 60,
    resolution_target_minutes       INTEGER NOT NULL DEFAULT 480,
    warning_before_breach_minutes   INTEGER NOT NULL DEFAULT 15,
    is_active                       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed: sensible defaults covering all priority levels
INSERT INTO sla_policy_configs
    (name, scope, first_response_target_minutes, resolution_target_minutes, warning_before_breach_minutes, is_active)
VALUES
    ('Global Default', 'GLOBAL', 60, 480, 15, TRUE);

INSERT INTO sla_policy_configs
    (name, scope, priority, first_response_target_minutes, resolution_target_minutes, warning_before_breach_minutes, is_active)
VALUES
    ('Low Priority SLA',      'PRIORITY', 'LOW',      240, 2880, 30, TRUE),
    ('Medium Priority SLA',   'PRIORITY', 'MEDIUM',   120, 1440, 20, TRUE),
    ('High Priority SLA',     'PRIORITY', 'HIGH',      30,  240, 10, TRUE),
    ('Critical Priority SLA', 'PRIORITY', 'CRITICAL',  15,  120,  5, TRUE);

-- ── Ticket SLA tracking fields ────────────────────────────────────────────────
ALTER TABLE tickets
    ADD COLUMN first_response_due_at      TIMESTAMP,
    ADD COLUMN resolution_due_at          TIMESTAMP,
    ADD COLUMN first_response_responded_at TIMESTAMP,
    ADD COLUMN resolved_at                TIMESTAMP;

-- Backfill resolved_at for tickets already in RESOLVED status
UPDATE tickets
SET    resolved_at = COALESCE(status_changed_at, updated_at)
WHERE  status = 'RESOLVED'
  AND  resolved_at IS NULL;

-- ── MailTemplate productivity metadata ────────────────────────────────────────
ALTER TABLE mail_templates
    ADD COLUMN supported_placeholders  TEXT,
    ADD COLUMN customer_visible        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN default_status_after_send VARCHAR(50);

-- Backfill built-in templates
UPDATE mail_templates
SET    supported_placeholders = '{replyBody},{ticketRef},{agentName},{mailboxName},{signatureBlock}',
       customer_visible = TRUE
WHERE  is_built_in = TRUE;

UPDATE mail_templates
SET    default_status_after_send = 'WAITING_CUSTOMER'
WHERE  code IN ('CUSTOMER_REPLY', 'NEED_MORE_INFO', 'FOLLOW_UP');

UPDATE mail_templates
SET    default_status_after_send = 'RESOLVED'
WHERE  code = 'ISSUE_RESOLVED';

-- ── Automation Rule Engine Foundation ────────────────────────────────────────
-- TODO: Automation rule evaluation pipeline is NOT active in this milestone.
-- The table provides the domain foundation for a future trigger/condition/action engine.
-- Conditions target: priority, status, tag, customer.
-- Actions target:    add tag, assign group, set priority, notify.
CREATE TABLE automation_rules (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255)  NOT NULL,
    description     VARCHAR(1000),
    trigger_type    VARCHAR(100)  NOT NULL,
    -- condition_json: {"field":"priority","op":"eq","value":"HIGH"}
    condition_json  TEXT,
    -- action_json: [{"type":"ADD_TAG","tagCode":"urgent"},{"type":"ASSIGN_GROUP","groupId":1}]
    action_json     TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT FALSE,
    execution_order INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
