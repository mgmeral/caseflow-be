package com.caseflow.email.domain;

/** Mail provider / vendor — controls auth defaults and IMAP preset values. */
public enum MailProvider {
    /** Google Workspace / Gmail — password or app-password based IMAP. */
    GMAIL,
    /** Microsoft 365 / Exchange Online — requires OAuth2/XOAUTH2. */
    OUTLOOK,
    /** Generic IMAP provider — no vendor-specific presets applied. */
    OTHER
}
