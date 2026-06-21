-- Security audit log — records security-significant events for compliance and forensics.
-- Append-only: rows are never updated or deleted.
CREATE TABLE security_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    event_type      VARCHAR(50)  NOT NULL,   -- e.g. LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, ACCOUNT_LOCKED, TOKEN_THEFT_DETECTED
    actor_user_id   BIGINT,                  -- who performed the action (null for pre-auth failures)
    actor_username  VARCHAR(150),            -- captured at event time (denormalised)
    ip_address      VARCHAR(45),             -- IPv4 or IPv6
    user_agent      VARCHAR(500),
    outcome         VARCHAR(20)  NOT NULL,   -- SUCCESS or FAILURE
    failure_reason  VARCHAR(200),            -- populated on FAILURE
    correlation_id  VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sal_actor_user_id ON security_audit_log (actor_user_id);
CREATE INDEX idx_sal_event_type    ON security_audit_log (event_type);
CREATE INDEX idx_sal_created_at    ON security_audit_log (created_at DESC);
