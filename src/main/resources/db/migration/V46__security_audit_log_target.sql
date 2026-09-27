-- What a security audit entry was about, e.g. INGRESS_EVENT:123 for an unmasked view of
-- personal data. Null for events whose subject is the actor itself (login, logout, ...).
ALTER TABLE security_audit_log ADD COLUMN target VARCHAR(100);

CREATE INDEX idx_sal_target ON security_audit_log (target);
