-- Phase 2-A: Jira integration configuration and ticket link tables

-- Global Jira configuration (one active config for the instance)
CREATE TABLE jira_configs (
    id              BIGSERIAL PRIMARY KEY,
    is_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    base_url        VARCHAR(1000) NOT NULL,
    auth_type       VARCHAR(50) NOT NULL DEFAULT 'BASIC',  -- BASIC only for Phase 2
    username        VARCHAR(500),
    -- Token is stored as plaintext in Phase 2; rotate to secrets-manager reference later.
    -- Never exposed raw in read APIs — masked to "****" in responses.
    api_token       TEXT,
    project_key     VARCHAR(100) NOT NULL,
    issue_type      VARCHAR(100) NOT NULL DEFAULT 'Task',
    default_labels  TEXT,       -- comma-separated, optional
    -- App base URL for generating back-links in Jira issue descriptions
    app_base_url    VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT
);

-- Phase 2 keeps a single global config; enforce this at the application layer.
COMMENT ON TABLE jira_configs IS
    'Global Jira integration settings. Phase 2 supports one active config per CaseFlow instance.';

COMMENT ON COLUMN jira_configs.api_token IS
    'Jira API token (Basic auth). Masked to **** in all read API responses.';

-- Stable link between a CaseFlow ticket and a Jira issue.
CREATE TABLE ticket_jira_links (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_id           BIGINT NOT NULL,
    ticket_public_id    UUID NOT NULL,
    jira_issue_key      VARCHAR(200) NOT NULL,
    jira_issue_id       VARCHAR(200),
    jira_url            VARCHAR(2000) NOT NULL,
    -- integration_job that created this link
    integration_job_id  BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          BIGINT,

    -- One active link per ticket; if jira_issue_key is later voided it must be explicitly removed.
    CONSTRAINT ticket_jira_links_ticket_unique UNIQUE (ticket_id)
);

CREATE INDEX idx_ticket_jira_links_ticket_id ON ticket_jira_links (ticket_id);
CREATE INDEX idx_ticket_jira_links_public_id ON ticket_jira_links (ticket_public_id);

COMMENT ON TABLE ticket_jira_links IS
    'Records the Jira issue created for a CaseFlow ticket. '
    'Unique per ticket — duplicate creates are rejected if a link or pending job already exists.';
