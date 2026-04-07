-- Phase 2: Seed new integration and scheduled-email permissions into roles
--
-- INTEGRATION_CONFIG_MANAGE → ADMIN only (config screens are admin-only)
-- INTEGRATION_JOB_VIEW      → ADMIN, SUPERVISOR (read-only monitoring)
-- SCHEDULED_EMAIL_MANAGE    → ADMIN, SUPERVISOR, AGENT (agents can schedule replies)

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES
    ('INTEGRATION_CONFIG_MANAGE'),
    ('INTEGRATION_JOB_VIEW'),
    ('SCHEDULED_EMAIL_MANAGE')
) AS p(code)
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = p.code
  );

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES
    ('INTEGRATION_JOB_VIEW'),
    ('SCHEDULED_EMAIL_MANAGE')
) AS p(code)
WHERE r.code = 'SUPERVISOR'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = p.code
  );

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES
    ('SCHEDULED_EMAIL_MANAGE')
) AS p(code)
WHERE r.code = 'AGENT'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = p.code
  );
