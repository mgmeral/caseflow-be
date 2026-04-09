-- V29: Outlook Business OAuth2 / XOAUTH2 inbound support
-- Adds mail_provider, auth_type, and OAuth2 credential columns to email_mailboxes.
-- Existing rows receive safe defaults: mail_provider=OTHER, auth_type=PASSWORD.

ALTER TABLE email_mailboxes
    ADD COLUMN mail_provider     VARCHAR(50)  NOT NULL DEFAULT 'OTHER',
    ADD COLUMN auth_type         VARCHAR(50)  NOT NULL DEFAULT 'PASSWORD',
    ADD COLUMN oauth_tenant_id   VARCHAR(255),
    ADD COLUMN oauth_client_id   VARCHAR(255),
    ADD COLUMN oauth_client_secret VARCHAR(512);
