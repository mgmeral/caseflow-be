-- V10: Email platform V6 enhancements
-- Adds operational metadata to mailboxes, extended settings to customer_email_settings,
-- and seeds new email/ticket-email permissions to the starter roles.

-- ── email_mailboxes: operational + routing metadata ───────────────────────────
ALTER TABLE email_mailboxes
    ADD COLUMN IF NOT EXISTS display_name               VARCHAR(255),
    ADD COLUMN IF NOT EXISTS default_group_id           BIGINT,
    ADD COLUMN IF NOT EXISTS default_priority           VARCHAR(50),
    ADD COLUMN IF NOT EXISTS last_successful_inbound_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_successful_outbound_at TIMESTAMPTZ;

-- ── customer_email_settings: extended routing controls ───────────────────────
ALTER TABLE customer_email_settings
    ADD COLUMN IF NOT EXISTS trusted_contacts_only BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS auto_create_contact   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS allow_subdomains      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS default_group_id      BIGINT,
    ADD COLUMN IF NOT EXISTS default_priority      VARCHAR(50);

-- ── role_permissions: seed V6 email permissions ───────────────────────────────

-- Admin: all new permissions (including ADMIN_CONFIG which was missing from V8)
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p
FROM roles r,
     unnest(ARRAY[
         'ADMIN_CONFIG',
         'EMAIL_CONFIG_VIEW','EMAIL_CONFIG_MANAGE',
         'EMAIL_OPERATIONS_VIEW','EMAIL_OPERATIONS_MANAGE',
         'TICKET_EMAIL_VIEW','TICKET_EMAIL_REPLY_SEND'
     ]) AS p
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = p
  );

-- Supervisor: email ops view + ticket email access
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p
FROM roles r,
     unnest(ARRAY[
         'EMAIL_CONFIG_VIEW',
         'EMAIL_OPERATIONS_VIEW',
         'TICKET_EMAIL_VIEW','TICKET_EMAIL_REPLY_SEND'
     ]) AS p
WHERE r.code = 'SUPERVISOR'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = p
  );

-- Agent: ticket email read + reply
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p
FROM roles r,
     unnest(ARRAY[
         'TICKET_EMAIL_VIEW','TICKET_EMAIL_REPLY_SEND'
     ]) AS p
WHERE r.code = 'AGENT'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = p
  );

-- Viewer: ticket email read only
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p
FROM roles r,
     unnest(ARRAY['TICKET_EMAIL_VIEW']) AS p
WHERE r.code = 'VIEWER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = p
  );
