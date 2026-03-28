-- Seed starter roles (editable templates — not immutable system roles)
INSERT INTO roles (code, name, description, ticket_scope) VALUES
    ('ADMIN',      'Administrator', 'Full system access',                      'ALL'),
    ('SUPERVISOR', 'Supervisor',    'Team supervisor with broad access',        'OWN_GROUPS'),
    ('AGENT',      'Agent',         'Standard support agent',                  'OWN_AND_OWN_GROUPS'),
    ('VIEWER',     'Viewer',        'Read-only access to assigned tickets',     'ASSIGNED_ONLY');

-- Admin: all permissions
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p
FROM roles r,
     unnest(ARRAY[
         'USER_MANAGE','ROLE_MANAGE','GROUP_MANAGE','TICKET_READ',
         'ADMIN_POOL_VIEW','TICKET_ASSIGN','TICKET_TRANSFER','TICKET_STATUS_CHANGE',
         'TICKET_CLOSE','TICKET_PRIORITY_CHANGE','CUSTOMER_REPLY_SEND','INTERNAL_NOTE_ADD',
         'REPORT_VIEW','DATA_EXPORT'
     ]) AS p
WHERE r.code = 'ADMIN';

-- Supervisor: team management + all ticket ops + reports
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p
FROM roles r,
     unnest(ARRAY[
         'GROUP_MANAGE','TICKET_READ','ADMIN_POOL_VIEW','TICKET_ASSIGN','TICKET_TRANSFER',
         'TICKET_STATUS_CHANGE','TICKET_CLOSE','TICKET_PRIORITY_CHANGE',
         'CUSTOMER_REPLY_SEND','INTERNAL_NOTE_ADD','REPORT_VIEW','DATA_EXPORT'
     ]) AS p
WHERE r.code = 'SUPERVISOR';

-- Agent: standard ticket work
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p
FROM roles r,
     unnest(ARRAY[
         'TICKET_READ','TICKET_STATUS_CHANGE','TICKET_CLOSE','TICKET_PRIORITY_CHANGE',
         'CUSTOMER_REPLY_SEND','INTERNAL_NOTE_ADD'
     ]) AS p
WHERE r.code = 'AGENT';

-- Viewer: read only
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p
FROM roles r,
     unnest(ARRAY['TICKET_READ']) AS p
WHERE r.code = 'VIEWER';

-- Wire users to the new roles table
ALTER TABLE users ADD COLUMN role_id BIGINT REFERENCES roles(id);

-- Backfill: map legacy role string (ADMIN/AGENT/VIEWER) to the seeded role ids
UPDATE users u
SET role_id = (SELECT r.id FROM roles r WHERE r.code = UPPER(u.role))
WHERE u.role IS NOT NULL;

-- Enforce NOT NULL now that all rows have been backfilled
ALTER TABLE users ALTER COLUMN role_id SET NOT NULL;

-- Drop the legacy string column — role_id is the canonical relation
ALTER TABLE users DROP COLUMN role;
