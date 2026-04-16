-- V37: Seed AI_ASSIST permission into roles
--
-- AI_ASSIST controls access to all /api/tickets/{id}/ai-* endpoints.
-- Granted to: ADMIN, SUPERVISOR, AGENT (all ticket-working roles).
-- NOT granted to read-only viewer roles (if any exist) by default.

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'AI_ASSIST'
FROM roles r
WHERE r.code IN ('ADMIN', 'SUPERVISOR', 'AGENT')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = 'AI_ASSIST'
  );
