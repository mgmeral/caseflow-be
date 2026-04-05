-- P1 closure: dedicated TICKET_TAG permission
--
-- Previously canTagTicket() used TICKET_STATUS_CHANGE, which is semantically wrong.
-- TICKET_TAG is now the correct permission for adding/removing tags on tickets.
-- ADMIN_CONFIG continues to govern the global tag vocabulary (TagController).
--
-- Granted to: ADMIN, SUPERVISOR, AGENT (same roles that had TICKET_STATUS_CHANGE)
-- Not granted to: VIEWER (read-only)

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'TICKET_TAG'
FROM roles r
WHERE r.code IN ('ADMIN', 'SUPERVISOR', 'AGENT')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = 'TICKET_TAG'
  );
