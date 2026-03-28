package com.caseflow.email.domain;

public enum ProviderType {
    /** Standard IMAP polling inbox. */
    IMAP,
    /** Webhook-based ingest (e.g. SendGrid, Mailgun Inbound). */
    WEBHOOK,
    /** SMTP relay — outbound only, no inbound capability. */
    SMTP_RELAY
}
