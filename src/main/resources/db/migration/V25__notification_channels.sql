-- Phase 2-B: External notification channel configs (Slack / Teams webhooks)

CREATE TABLE notification_channel_configs (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    channel_type        VARCHAR(50)  NOT NULL,   -- SLACK | TEAMS
    is_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Webhook URL. Stored plaintext; masked to **** in read responses.
    webhook_url         TEXT         NOT NULL,
    -- JSON array of NotificationEventType strings this config subscribes to.
    -- e.g. ["TICKET_CREATED","TICKET_RESOLVED"]
    subscribed_events   TEXT         NOT NULL DEFAULT '[]',
    -- Scope: GLOBAL sends for all events, GROUP/CUSTOMER scopes to a specific entity.
    scope_type          VARCHAR(50)  NOT NULL DEFAULT 'GLOBAL',   -- GLOBAL | GROUP | CUSTOMER
    scope_id            BIGINT,      -- group_id or customer_id; null for GLOBAL
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          BIGINT
);

CREATE INDEX idx_notification_channel_enabled
    ON notification_channel_configs (channel_type, is_enabled);

CREATE INDEX idx_notification_channel_scope
    ON notification_channel_configs (scope_type, scope_id)
    WHERE scope_type != 'GLOBAL';

COMMENT ON TABLE notification_channel_configs IS
    'Configures external webhook notification channels (Slack, Teams). '
    'Each config subscribes to a subset of ticket events and may be scoped globally, '
    'to a group, or to a specific customer.';

COMMENT ON COLUMN notification_channel_configs.subscribed_events IS
    'JSON array of event codes this config receives notifications for. '
    'Empty array means no events — effectively disabled. '
    'Example: ["TICKET_CREATED","TICKET_RESOLVED","OUTBOUND_REPLY_FAILED"]';

COMMENT ON COLUMN notification_channel_configs.scope_type IS
    'GLOBAL — all tickets trigger this channel. '
    'GROUP — only tickets whose assigned_group_id matches scope_id. '
    'CUSTOMER — only tickets whose customer_id matches scope_id.';
