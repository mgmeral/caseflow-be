package com.caseflow.email.domain;

public enum OutboundMode {
    /** Send via configured SMTP server. */
    SMTP,
    /** Route through an SMTP relay / API gateway (SendGrid, SES, etc.). */
    RELAY
}
