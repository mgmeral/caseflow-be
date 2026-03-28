INSERT INTO users (
    username,
    email,
    full_name,
    is_active,
    password_hash,
    role
)
SELECT
    'admin',
    'admin@caseflow.local',
    'Admin',
    true,
    '$2a$12$mpwlwlJW1IVaBHWUpue2ZuSO70Clf45pMOMe/1Dt7cehhF1LTqtRq',
    'ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'admin'
);