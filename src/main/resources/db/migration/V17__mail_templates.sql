-- V17: Mail template CRUD + references_header for RFC 2822 threading

-- ── mail_templates table ────────────────────────────────────────────────────
CREATE TABLE mail_templates (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(100) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    subject_template    VARCHAR(500),
    html_template       TEXT         NOT NULL,
    plain_text_template TEXT         NOT NULL,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    is_built_in         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL
);

-- ── references_header column ────────────────────────────────────────────────
ALTER TABLE outbound_email_dispatches
    ADD COLUMN references_header VARCHAR(2048);

-- ── Seed built-in CUSTOMER_REPLY template ───────────────────────────────────
INSERT INTO mail_templates (
    code, name, subject_template,
    html_template, plain_text_template,
    is_active, is_built_in, created_at, updated_at
) VALUES (
    'CUSTOMER_REPLY',
    'Customer Reply',
    NULL,
    '<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="font-family:Arial,Helvetica,sans-serif;color:#1a1a1a;max-width:640px;margin:0 auto;padding:24px 16px;line-height:1.5">
  <div style="margin-bottom:24px">
    <div style="white-space:pre-wrap">{replyBody}</div>
  </div>
  <hr style="border:none;border-top:1px solid #e0e0e0;margin:0 0 16px 0">
  <p style="font-size:12px;color:#888;margin:0">Ticket: {ticketRef}</p>
</body>
</html>',
    '{replyBody}

---
Ticket: {ticketRef}
',
    TRUE,
    TRUE,
    NOW(),
    NOW()
);

-- ── Seed built-in TICKET_RECEIVED_ACK template ──────────────────────────────
INSERT INTO mail_templates (
    code, name, subject_template,
    html_template, plain_text_template,
    is_active, is_built_in, created_at, updated_at
) VALUES (
    'TICKET_RECEIVED_ACK',
    'Ticket Received Acknowledgement',
    'We received your request — {ticketRef}',
    '<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="font-family:Arial,Helvetica,sans-serif;color:#1a1a1a;max-width:640px;margin:0 auto;padding:24px 16px;line-height:1.5">
  <div style="margin-bottom:24px">
    <div style="white-space:pre-wrap">{replyBody}</div>
  </div>
  <hr style="border:none;border-top:1px solid #e0e0e0;margin:0 0 16px 0">
  <p style="font-size:12px;color:#888;margin:0">Ticket: {ticketRef}</p>
</body>
</html>',
    '{replyBody}

---
Ticket: {ticketRef}
',
    TRUE,
    TRUE,
    NOW(),
    NOW()
);
