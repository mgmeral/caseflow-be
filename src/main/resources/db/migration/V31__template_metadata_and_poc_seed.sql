-- V31: Add usageType / description metadata to mail_templates; seed POC-ready templates

ALTER TABLE mail_templates
    ADD COLUMN usage_type   VARCHAR(50),
    ADD COLUMN description  VARCHAR(1000);

-- Backfill existing seeded templates
UPDATE mail_templates SET
    usage_type  = 'CUSTOMER_REPLY',
    description = 'Standard reply to a customer inquiry. Use {replyBody} for the agent''s response.'
WHERE code = 'CUSTOMER_REPLY';

UPDATE mail_templates SET
    usage_type  = 'ACKNOWLEDGEMENT',
    description = 'Automatic acknowledgement sent when a new ticket is received.'
WHERE code = 'TICKET_RECEIVED_ACK';

-- ── New POC templates ────────────────────────────────────────────────────────

INSERT INTO mail_templates (
    code, name, usage_type, description,
    subject_template, html_template, plain_text_template,
    is_active, is_built_in, created_at, updated_at
) VALUES (
    'ACKNOWLEDGEMENT',
    'New Ticket Acknowledgement',
    'ACKNOWLEDGEMENT',
    'Sent to the customer when a ticket is first created. Confirms receipt and sets expectations.',
    'We received your request — {ticketRef}',
    '<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="font-family:Arial,Helvetica,sans-serif;color:#1a1a1a;max-width:640px;margin:0 auto;padding:24px 16px;line-height:1.5">
  <p>Thank you for reaching out. We have received your request and one of our team members will be in touch shortly.</p>
  <div style="margin:16px 0;white-space:pre-wrap">{replyBody}</div>
  <hr style="border:none;border-top:1px solid #e0e0e0;margin:16px 0">
  <p style="font-size:12px;color:#888;margin:0">Reference: {ticketRef}</p>
</body>
</html>',
    'Thank you for reaching out. We have received your request and one of our team members will be in touch shortly.

{replyBody}

---
Reference: {ticketRef}
',
    TRUE, TRUE, NOW(), NOW()
);

INSERT INTO mail_templates (
    code, name, usage_type, description,
    subject_template, html_template, plain_text_template,
    is_active, is_built_in, created_at, updated_at
) VALUES (
    'NEED_MORE_INFO',
    'Need More Information',
    'NEED_MORE_INFO',
    'Request additional details from the customer to progress the ticket.',
    'We need a little more information — {ticketRef}',
    '<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="font-family:Arial,Helvetica,sans-serif;color:#1a1a1a;max-width:640px;margin:0 auto;padding:24px 16px;line-height:1.5">
  <p>Thank you for your message. To help us resolve your issue as quickly as possible, could you please provide the following additional information?</p>
  <div style="margin:16px 0;white-space:pre-wrap">{replyBody}</div>
  <hr style="border:none;border-top:1px solid #e0e0e0;margin:16px 0">
  <p style="font-size:12px;color:#888;margin:0">Ticket: {ticketRef}</p>
</body>
</html>',
    'Thank you for your message. To help us resolve your issue as quickly as possible, could you please provide the following additional information?

{replyBody}

---
Ticket: {ticketRef}
',
    TRUE, TRUE, NOW(), NOW()
);

INSERT INTO mail_templates (
    code, name, usage_type, description,
    subject_template, html_template, plain_text_template,
    is_active, is_built_in, created_at, updated_at
) VALUES (
    'FOLLOW_UP',
    'Follow Up',
    'FOLLOW_UP',
    'Follow up with the customer when the ticket is waiting for their response.',
    'Following up on your request — {ticketRef}',
    '<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="font-family:Arial,Helvetica,sans-serif;color:#1a1a1a;max-width:640px;margin:0 auto;padding:24px 16px;line-height:1.5">
  <p>We wanted to follow up on your open request. Please let us know if you have any updates or if there is anything else we can help you with.</p>
  <div style="margin:16px 0;white-space:pre-wrap">{replyBody}</div>
  <hr style="border:none;border-top:1px solid #e0e0e0;margin:16px 0">
  <p style="font-size:12px;color:#888;margin:0">Ticket: {ticketRef}</p>
</body>
</html>',
    'We wanted to follow up on your open request. Please let us know if you have any updates or if there is anything else we can help you with.

{replyBody}

---
Ticket: {ticketRef}
',
    TRUE, TRUE, NOW(), NOW()
);

INSERT INTO mail_templates (
    code, name, usage_type, description,
    subject_template, html_template, plain_text_template,
    is_active, is_built_in, created_at, updated_at
) VALUES (
    'ISSUE_RESOLVED',
    'Issue Resolved',
    'RESOLUTION',
    'Notify the customer that their issue has been resolved and the ticket is closing.',
    'Your request has been resolved — {ticketRef}',
    '<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="font-family:Arial,Helvetica,sans-serif;color:#1a1a1a;max-width:640px;margin:0 auto;padding:24px 16px;line-height:1.5">
  <p>We are pleased to let you know that your request has been resolved.</p>
  <div style="margin:16px 0;white-space:pre-wrap">{replyBody}</div>
  <p>If you have any further questions or the issue recurs, please do not hesitate to contact us and we will be happy to help.</p>
  <hr style="border:none;border-top:1px solid #e0e0e0;margin:16px 0">
  <p style="font-size:12px;color:#888;margin:0">Ticket: {ticketRef}</p>
</body>
</html>',
    'We are pleased to let you know that your request has been resolved.

{replyBody}

If you have any further questions or the issue recurs, please do not hesitate to contact us.

---
Ticket: {ticketRef}
',
    TRUE, TRUE, NOW(), NOW()
);
